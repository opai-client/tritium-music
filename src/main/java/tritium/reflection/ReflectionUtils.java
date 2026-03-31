package tritium.reflection;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import me.fan87.nativeinstrumentation.NativeInstrumentation;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @author IzumiiKonata
 * Date: 2025/8/17 19:01
 */
@UtilityClass
public class ReflectionUtils {

    public boolean replaceSequenceWith(InsnList instructions, int[] byteCodeSequence, List<AbstractInsnNode> replaceWith) {

        List<AbstractInsnNode> instReplaceStarts = new ArrayList<>();

        allInstructions:
        for (AbstractInsnNode insn : instructions) {

            AbstractInsnNode current = insn;

            for (int opcode : byteCodeSequence) {

                if (current == null)
                    continue allInstructions;

                if (current.getOpcode() != opcode)
                    continue allInstructions;

                current = current.getNext();
            }

            // ok 找到了一个序列 给最开始的指令塞到 instReplaceStarts 里
            instReplaceStarts.add(insn);
        }

        // 如果没找到就 return false
        if (instReplaceStarts.isEmpty()) {
            return false;
        }

        // 从后往前处理，避免位置偏移问题
        Collections.reverse(instReplaceStarts);

        for (AbstractInsnNode instReplaceStart : instReplaceStarts) {

            AbstractInsnNode endNode = instReplaceStart;

            // 找到序列的最后一个指令
            // 这里没 - 1 是故意的 寻找序列的下一条指令
            for (int i = 0; i < byteCodeSequence.length; i++) {
                endNode = endNode.getNext();
            }

            // 先插入新指令，再删除旧指令
            // 注意：每次使用都要克隆指令节点
            List<AbstractInsnNode> clonedInstructions = cloneInstructions(replaceWith);

            // 在序列开始之前插入新指令
            AbstractInsnNode insertPoint = instReplaceStart;
            for (AbstractInsnNode insnNode : clonedInstructions) {
                instructions.insertBefore(insertPoint, insnNode);
            }

            // 删除原序列
            AbstractInsnNode nodeToRemove = instReplaceStart;
            for (int i = 0; i < byteCodeSequence.length; i++) {
                AbstractInsnNode next = nodeToRemove.getNext();
                instructions.remove(nodeToRemove);
                nodeToRemove = next;
            }
        }

        return true;
    }

    /**
     * 克隆指令列表，解决节点重复使用的问题
     */
    private List<AbstractInsnNode> cloneInstructions(List<AbstractInsnNode> original) {
        List<AbstractInsnNode> cloned = new ArrayList<>();
        Map<LabelNode, LabelNode> labelMap = new HashMap<>();

        for (AbstractInsnNode instruction : original) {
            cloned.add(cloneInstruction(instruction, labelMap));
        }

        return cloned;
    }

    /**
     * 克隆单个指令节点
     */
    private AbstractInsnNode cloneInstruction(AbstractInsnNode instruction, Map<LabelNode, LabelNode> labelMap) {
        switch (instruction.getType()) {
            case AbstractInsnNode.INSN:
                return new InsnNode(instruction.getOpcode());

            case AbstractInsnNode.INT_INSN:
                IntInsnNode intInsn = (IntInsnNode) instruction;
                return new IntInsnNode(intInsn.getOpcode(), intInsn.operand);

            case AbstractInsnNode.VAR_INSN:
                VarInsnNode varInsn = (VarInsnNode) instruction;
                return new VarInsnNode(varInsn.getOpcode(), varInsn.var);

            case AbstractInsnNode.TYPE_INSN:
                TypeInsnNode typeInsn = (TypeInsnNode) instruction;
                return new TypeInsnNode(typeInsn.getOpcode(), typeInsn.desc);

            case AbstractInsnNode.FIELD_INSN:
                FieldInsnNode fieldInsn = (FieldInsnNode) instruction;
                return new FieldInsnNode(fieldInsn.getOpcode(), fieldInsn.owner, fieldInsn.name, fieldInsn.desc);

            case AbstractInsnNode.METHOD_INSN:
                MethodInsnNode methodInsn = (MethodInsnNode) instruction;
                return new MethodInsnNode(methodInsn.getOpcode(), methodInsn.owner, methodInsn.name, methodInsn.desc, methodInsn.itf);

            case AbstractInsnNode.INVOKE_DYNAMIC_INSN:
                InvokeDynamicInsnNode invokeDynamicInsn = (InvokeDynamicInsnNode) instruction;
                return new InvokeDynamicInsnNode(invokeDynamicInsn.name, invokeDynamicInsn.desc, invokeDynamicInsn.bsm, invokeDynamicInsn.bsmArgs);

            case AbstractInsnNode.JUMP_INSN:
                JumpInsnNode jumpInsn = (JumpInsnNode) instruction;
                LabelNode newLabel = labelMap.computeIfAbsent(jumpInsn.label, k -> new LabelNode());
                return new JumpInsnNode(jumpInsn.getOpcode(), newLabel);

            case AbstractInsnNode.LABEL:
                LabelNode labelInsn = (LabelNode) instruction;
                return labelMap.computeIfAbsent(labelInsn, k -> new LabelNode());

            case AbstractInsnNode.LDC_INSN:
                LdcInsnNode ldcInsn = (LdcInsnNode) instruction;
                return new LdcInsnNode(ldcInsn.cst);

            case AbstractInsnNode.IINC_INSN:
                IincInsnNode iincInsn = (IincInsnNode) instruction;
                return new IincInsnNode(iincInsn.var, iincInsn.incr);

            case AbstractInsnNode.TABLESWITCH_INSN:
                TableSwitchInsnNode tableSwitchInsn = (TableSwitchInsnNode) instruction;
                LabelNode newDefaultLabel = labelMap.computeIfAbsent(tableSwitchInsn.dflt, k -> new LabelNode());
                List<LabelNode> newLabels = new ArrayList<>();
                for (LabelNode label : tableSwitchInsn.labels) {
                    newLabels.add(labelMap.computeIfAbsent(label, k -> new LabelNode()));
                }
                return new TableSwitchInsnNode(tableSwitchInsn.min, tableSwitchInsn.max, newDefaultLabel, newLabels.toArray(new LabelNode[0]));

            case AbstractInsnNode.LOOKUPSWITCH_INSN:
                LookupSwitchInsnNode lookupSwitchInsn = (LookupSwitchInsnNode) instruction;
                LabelNode newDefaultLabel2 = labelMap.computeIfAbsent(lookupSwitchInsn.dflt, k -> new LabelNode());
                List<LabelNode> newLabels2 = new ArrayList<>();
                for (LabelNode label : lookupSwitchInsn.labels) {
                    newLabels2.add(labelMap.computeIfAbsent(label, k -> new LabelNode()));
                }
                int[] arr = new int[lookupSwitchInsn.keys.size()];

                List<Integer> keys = lookupSwitchInsn.keys;
                for (int i = 0; i < keys.size(); i++) {
                    arr[i] = keys.get(i);
                }

                return new LookupSwitchInsnNode(newDefaultLabel2,
                        arr,
                        newLabels2.toArray(new LabelNode[0]));

            case AbstractInsnNode.MULTIANEWARRAY_INSN:
                MultiANewArrayInsnNode multiArrayInsn = (MultiANewArrayInsnNode) instruction;
                return new MultiANewArrayInsnNode(multiArrayInsn.desc, multiArrayInsn.dims);

            case AbstractInsnNode.FRAME:
                FrameNode frameInsn = (FrameNode) instruction;
                // Frame 节点需要特殊处理，这里简化处理
                // 在实际使用时可能需要更复杂的克隆逻辑
                return new FrameNode(frameInsn.type, frameInsn.local != null ? frameInsn.local.size() : 0,
                        frameInsn.local != null ? frameInsn.local.toArray() : null,
                        frameInsn.stack != null ? frameInsn.stack.size() : 0,
                        frameInsn.stack != null ? frameInsn.stack.toArray() : null);

            case AbstractInsnNode.LINE:
                LineNumberNode lineNumberInsn = (LineNumberNode) instruction;
                LabelNode newStartLabel = labelMap.computeIfAbsent(lineNumberInsn.start, k -> new LabelNode());
                return new LineNumberNode(lineNumberInsn.line, newStartLabel);

            default:
                // 对于未知类型，返回 NOP 指令
                return new InsnNode(org.objectweb.asm.Opcodes.NOP);
        }
    }

    @SneakyThrows
    public byte[] getClassBytes(Class<?> clazz) {
        String className = clazz.getName().replace('.', '/') + ".class";

        InputStream inputStream = clazz.getClassLoader().getResourceAsStream(className);
        if (inputStream == null) {
            throw new IllegalArgumentException("Class not found: " + clazz.getName());
        }

        byte[] buffer = new byte[1024];
        int bytesRead;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            byteArrayOutputStream.write(buffer, 0, bytesRead);
        }
        inputStream.close();

        return byteArrayOutputStream.toByteArray();
    }

    public ClassNode getClassNode(Class<?> clazz) {
        byte[] classBytes = getClassBytes(clazz);
        ClassReader classReader = new ClassReader(classBytes);
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, ClassReader.EXPAND_FRAMES);
        return classNode;
    }

    public byte[] toByteArray(ClassNode classNode) {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(classWriter);
        return classWriter.toByteArray();
    }

    private boolean checkJar(File fileIn, String dir) {

        try (JarFile jarFile = new JarFile(fileIn)) {
            // 获取JAR文件中的所有条目
            Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                // 检查文件夹名是否匹配
                if (entry.getName().startsWith(dir)) {
                    return true;
                }
            }

        } catch (IOException e) {
            StringWriter stringWriter = new StringWriter();
            PrintWriter s = new PrintWriter(stringWriter);
            e.printStackTrace(s);

            System.out.println(Base64.getEncoder().encodeToString(stringWriter.toString().getBytes(StandardCharsets.UTF_8)));
        }

        return false;
    }

    @SneakyThrows
    public void addClassPaths(String dir) {

        File curDir = Paths.get("").toAbsolutePath().normalize().toFile();

        // cn ver.
        File extensionsFolder = new File(new File(new File(System.getenv("APPDATA")), "Opai"), "extensions");

        if (extensionsFolder.exists() && extensionsFolder.isDirectory()) {
            for (File file : extensionsFolder.listFiles()) {

                if (file.getName().toLowerCase().endsWith(".jar") && checkJar(file, dir)) {
                    addURLToClassLoader(file, ClassLoader.getSystemClassLoader());
                    System.out.println("Added " + file.getAbsolutePath() + " to classpath.");
                    return;
                }

            }
        }

        loop:
        while (true) {

            File parent = curDir;
            System.out.println("Scanning Directory: " + parent.getAbsolutePath());

            listParent:
            for (File file : parent.listFiles()) {

                if (file.getName().equals("Opai") && file.isDirectory()) {
                    File extensionsFolderGlobal = new File(file, "extensions");

                    if (!extensionsFolderGlobal.exists() || !extensionsFolderGlobal.isDirectory()) {
                        continue listParent;
                    }

                    for (File file2 : extensionsFolderGlobal.listFiles()) {


                        if (file2.getName().toLowerCase().endsWith(".jar") && checkJar(file2, dir)) {
                            addURLToClassLoader(file2, ClassLoader.getSystemClassLoader());
                            System.out.println("Added " + file2.getAbsolutePath() + " to classpath.");
                            break loop;
                        }

                    }

                    break loop;
                }

            }

            curDir = curDir.getParentFile();

            if (curDir == null)
                break;
        }

    }

    @SneakyThrows
    public void addURLToClassLoader(File directoryToAdd, ClassLoader classLoader) {

        try {
            Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);

            method.invoke(classLoader, directoryToAdd.toURI().toURL());
            return;
        } catch (Exception e) {

        }

        // Java9+ stuff
        NativeInstrumentation.doPrivileged(() -> {

            try {
                ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
                System.out.println("systemClassLoader: " + systemClassLoader.getClass().getName());
                NativeInstrumentation.invokeMethodS(systemClassLoader.getClass(), systemClassLoader, "appendToClassPathForInstrumentation", "(Ljava/lang/String;)V", directoryToAdd.getAbsolutePath());

            } catch (Throwable t) {
                t.printStackTrace();
            }

        });

    }

}
