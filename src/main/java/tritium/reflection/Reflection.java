package tritium.reflection;

import com.google.common.base.Splitter;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import me.fan87.nativeinstrumentation.NativeInstrumentation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.*;
import org.objectweb.asm.Label;
import today.opai.api.OpenAPI;
import today.opai.api.events.EventRender2D;
import today.opai.api.interfaces.EventHandler;
import tritium.interfaces.SharedConstants;
import tritium.utils.other.multithreading.MultiThreadingUtil;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.util.Map;
import java.util.Set;

import static java.lang.reflect.Modifier.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * @author IzumiiKonata
 * Date: 2025/5/10 10:48
 */
@UtilityClass
public class Reflection {

    boolean debug = true;

    private final Logger LOGGER = LogManager.getLogger("Reflection");
    public boolean DYNAMIC_ISLAND_SUPPORTED = false;

    public EventHandler handler = new EventHandler() {

        int frameCounter = 0;

        @Override
        public void onRender2D(EventRender2D event) {

            frameCounter += 1;

            if (frameCounter < 10)
                return;

            MultiThreadingUtil.runAsync(Reflection::initDynIsland);

            SharedConstants.api.unregisterEvent(handler);
        }
    };

    @SneakyThrows
    public void init(OpenAPI api) {
        api.registerEvent(handler);
    }

    Class<?> DYN_1, DynamicIslandClass, DynamicIslandEntity, ResourceLocation,
                    IResourcePack, DefaultResourcePack;

    public void initDynIsland() {

        try {
            DYN_1 = ClassFinder.finder()

                    .addField(Splitter.class, PRIVATE | STATIC | FINAL)
                    .addField(Logger.class, PRIVATE | STATIC | FINAL)

                    .addMethod(void.class, PUBLIC)
                    .addMethod(void.class, PUBLIC)
                    .addMethod(void.class, PUBLIC, Object.class, boolean.class)
                    .addMethod(void.class, PRIVATE, Object.class)

                    .addMethod(Splitter.class, ClassFinder.DONT_CARE)
                    .addMethod(Logger.class, ClassFinder.DONT_CARE)
                    .addMethod(void.class, ClassFinder.DONT_CARE, Object.class, Object.class)

                    .find();

            if (debug)
                System.out.println("Found DYN_1: " + DYN_1.getName());

            DynamicIslandClass = ClassFinder.finder()
                    .addField(DYN_1, PRIVATE | FINAL)

                    .addMethod(boolean.class, PUBLIC)
                    .addMethod(String.class, PRIVATE | STATIC, byte[].class)
                    .addMethod(String.class, PRIVATE | STATIC, int.class, long.class)

                    .find();

            if (debug)
                System.out.println("Found DynamicIslandClass: " + DynamicIslandClass.getName());

            ResourceLocation = CommonReflectionClasses.ResourceLocation.get();
            if (debug)
                System.out.println("Found ResourceLocation: " + ResourceLocation.getName());

            DynamicIslandEntity = ClassFinder.finder()

                    .setStrictMode(ClassFinder.Finder.StrictMode.Fields)
                    .addField(DynamicIslandClass, ClassFinder.DONT_CARE)
                    .addField(String.class, ClassFinder.DONT_CARE)
                    .addField(Color.class, ClassFinder.DONT_CARE)
                    .addField(ResourceLocation, ClassFinder.DONT_CARE)

                    .find();

            if (debug)
                System.out.println("Found DynamicIslandEntity: " + DynamicIslandClass.getName());

            IResourcePack = ClassFinder.finder()

                    .setInterface()

                    .addMethod(InputStream.class, ClassFinder.DONT_CARE, ResourceLocation)
                    .addMethod(boolean.class, ClassFinder.DONT_CARE, ResourceLocation)
                    .addMethod(Set.class, ClassFinder.DONT_CARE)
                    .addMethod(Object.class, ClassFinder.DONT_CARE, Object.class, String.class)
                    .addMethod(BufferedImage.class, ClassFinder.DONT_CARE)
                    .addMethod(String.class, ClassFinder.DONT_CARE)

                    .find();

            if (debug)
                System.out.println("Found IResourcePack: " + IResourcePack.getName());

            DefaultResourcePack = ClassFinder.finder()

                    .implementsClass(IResourcePack)

                    .addField(Set.class, PUBLIC | STATIC | FINAL)
                    .addField(Map.class, PRIVATE | FINAL)

                    .find();

            if (debug)
                System.out.println("Found DefaultResourcePack: " + DefaultResourcePack.getName());

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to locate watermark class!");
            return;
        }

        try {

            Instrumentation inst = NativeInstrumentation.getInstance();

            byte[] transformedDynIsland = transformDynIsland(DynamicIslandClass, DynamicIslandEntity, ResourceLocation);

//            Files.write(new File("D:\\DynIsland.class").toPath(), transformedDynIsland, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

            inst.redefineClasses(new ClassDefinition(DynamicIslandClass, transformedDynIsland), new ClassDefinition(DefaultResourcePack, transformDefaultResourcePack()));

            DYNAMIC_ISLAND_SUPPORTED = true;
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to transform watermark class!");
        }
    }

    public void restoreDynIsland() {
        try {
            byte[] classBytes = ReflectionUtils.getClassBytes(DynamicIslandClass);

            Instrumentation inst = NativeInstrumentation.getInstance();
            inst.redefineClasses(new ClassDefinition(DynamicIslandClass, classBytes));
            DYNAMIC_ISLAND_SUPPORTED = true;
        } catch (Exception e) {
            LOGGER.warn("Failed to transform watermark class!", e);
        }
    }

    public byte[] transformDefaultResourcePack() {
        byte[] origIn = ReflectionUtils.getClassBytes(DefaultResourcePack);

        ClassReader classReader = new ClassReader(origIn);
        ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        classReader.accept(new ClassVisitor(ASM9, classWriter) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {

                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                if (descriptor.endsWith(")Ljava/io/InputStream;")) {
                    return new MethodVisitor(ASM9, mv) {
                        @Override
                        public void visitCode() {

                            Label lblElse = new Label();

                            // hook DefaultResourcePack 为歌词svg返回自己的 InputStream
                            // if ((aload 1).toString().endsWith("textures/lyrics.svg"))
                            //      return new ByteArrayInputStream("<svg data>".getBytes());
                            mv.visitVarInsn(ALOAD, 1);
                            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false);
                            mv.visitLdcInsn("textures/lyrics.svg");
                            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "endsWith", "(Ljava/lang/String;)Z", false);
                            mv.visitJumpInsn(IFEQ, lblElse);

                            mv.visitTypeInsn(NEW, "java/io/ByteArrayInputStream");
                            mv.visitInsn(DUP);
                            mv.visitLdcInsn("<?xml version=\"1.0\" encoding=\"utf-8\"?><svg width=\"800px\" height=\"800px\" viewBox=\"0 0 24 24\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">\n<path d=\"M10.0909 11.9629L19.3636 8.63087V14.1707C18.8126 13.8538 18.1574 13.67 17.4545 13.67C15.4964 13.67 13.9091 15.096 13.9091 16.855C13.9091 18.614 15.4964 20.04 17.4545 20.04C19.4126 20.04 21 18.614 21 16.855C21 16.855 21 16.8551 21 16.855L21 7.49236C21 6.37238 21 5.4331 20.9123 4.68472C20.8999 4.57895 20.8852 4.4738 20.869 4.37569C20.7845 3.86441 20.6352 3.38745 20.347 2.98917C20.2028 2.79002 20.024 2.61055 19.8012 2.45628C19.7594 2.42736 19.716 2.39932 19.6711 2.3722L19.6621 2.36679C18.8906 1.90553 18.0233 1.93852 17.1298 2.14305C16.2657 2.34086 15.1944 2.74368 13.8808 3.23763L11.5963 4.09656C10.9806 4.32806 10.4589 4.52419 10.0494 4.72734C9.61376 4.94348 9.23849 5.1984 8.95707 5.57828C8.67564 5.95817 8.55876 6.36756 8.50501 6.81203C8.4545 7.22978 8.45452 7.7378 8.45455 8.33743V16.1307C7.90347 15.8138 7.24835 15.63 6.54545 15.63C4.58735 15.63 3 17.056 3 18.815C3 20.574 4.58735 22 6.54545 22C8.50355 22 10.0909 20.574 10.0909 18.815C10.0909 18.815 10.0909 18.8151 10.0909 18.815L10.0909 11.9629Z\" fill=\"#FFFFFF\"/>\n</svg>");
                            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "getBytes", "()[B", false);
                            mv.visitMethodInsn(INVOKESPECIAL, "java/io/ByteArrayInputStream", "<init>", "([B)V", false);
                            mv.visitInsn(ARETURN);

                            mv.visitLabel(lblElse);

                            super.visitCode();
                        }
                    };
                }

                return mv;
            }
        }, 0);

        return classWriter.toByteArray();
    }

    public byte[] transformDynIsland(Class<?> DynamicIsland, Class<?> entityClass, Class<?> ResourceLocation) {

        byte[] origIn = ReflectionUtils.getClassBytes(DynamicIsland);

        ClassReader classReader = new ClassReader(origIn);
        ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        classReader.accept(new ClassVisitor(ASM9, classWriter) {

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (descriptor.endsWith(";)Z")) {
                    return new MethodVisitor(ASM9, mv) {

                        boolean visited = false;

                        @Override
                        @SneakyThrows
                        public void visitVarInsn(int opcode, int varIndex) {
                            super.visitVarInsn(opcode, varIndex);

                            if (opcode == FSTORE && varIndex == 7 && !visited) {
                                this.visited = true;

                                Label elseLabel = new Label();

                                Label innerLabel = new Label();
                                Label outerLabel = new Label();

                                // new local var index
                                int vIndex = 10;
                                // dyn island entity list var index
                                int listIndex = 6;

                                {
                                    // 获取动态岛歌词
                                    // vIndex = System.getProperty("ncm.dynIslandLyrics", "");
                                    mv.visitLdcInsn("ncm.dynIslandLyrics");
                                    mv.visitLdcInsn("");
                                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "getProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                                    mv.visitVarInsn(ASTORE, vIndex);

                                    // if (vIndex.isEmpty())
                                    //      goto elselabel;
                                    mv.visitVarInsn(ALOAD, vIndex);
                                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "isEmpty", "()Z", false);

                                    mv.visitJumpInsn(IFNE, elseLabel);
                                }

                                // 移除第二项后面的所有项目 (只保留 Opai watermark 和用户名)
                                // while (aEntityList.size() > 2)
                                //      aEntityList.remove(aEntityList.size() - 1);
                                {
                                    mv.visitLabel(innerLabel);
                                    mv.visitVarInsn(ALOAD, listIndex);
                                    mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "size", "()I", true);
                                    mv.visitInsn(ICONST_2);
                                    mv.visitJumpInsn(IF_ICMPLE, outerLabel);

                                    mv.visitVarInsn(ALOAD, listIndex);
                                    mv.visitVarInsn(ALOAD, listIndex);
                                    mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "size", "()I", true);
                                    mv.visitInsn(ICONST_1);
                                    mv.visitInsn(ISUB);
                                    mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "remove", "(I)Ljava/lang/Object;", true);
                                    mv.visitInsn(POP);
                                    mv.visitJumpInsn(GOTO, innerLabel);
                                }

                                // 将歌词添加到动态岛实体列表中
                                {
                                    mv.visitLabel(outerLabel);
                                    mv.visitVarInsn(ALOAD, listIndex);
                                    String entityClassName = entityClass.getName().replace(".", "/");
                                    String resourceLocationName = ResourceLocation.getName().replace(".", "/");

                                    // Entity aEntity = new Entity(dynIslandInstance, svgResourceLocation, new Color(56, 93, 56), <lyrics string>, <unknown integer>);
                                    // new Entity
                                    mv.visitTypeInsn(NEW, entityClassName);
                                    mv.visitInsn(DUP);
                                    mv.visitVarInsn(ALOAD, 0);
                                    // new ResourceLocation
                                    mv.visitTypeInsn(NEW, resourceLocationName);
                                    mv.visitInsn(DUP);
//                                mv.visitVarInsn(ALOAD, 0);
                                    mv.visitLdcInsn("textures/lyrics.svg");
                                    mv.visitMethodInsn(INVOKESPECIAL, resourceLocationName, "<init>", "(Ljava/lang/String;)V", false);
                                    // new Color
                                    mv.visitTypeInsn(NEW, "java/awt/Color");
                                    mv.visitInsn(DUP);
                                    mv.visitIntInsn(SIPUSH, 150);
                                    mv.visitIntInsn(SIPUSH, 239);
                                    mv.visitIntInsn(BIPUSH, 56);
                                    mv.visitMethodInsn(INVOKESPECIAL, "java/awt/Color", "<init>", "(III)V", false);
                                    mv.visitVarInsn(ALOAD, vIndex);
                                    mv.visitLdcInsn(-1816927672);
                                    mv.visitMethodInsn(INVOKESPECIAL, entityClassName, "<init>", String.format(
                                            "(L%s;L%s;Ljava/awt/Color;Ljava/lang/String;I)V",
                                            DynamicIsland.getName().replace(".", "/"),
                                            resourceLocationName
                                    ), false);
                                    // aEntityList.add(aEntity);
                                    mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
                                    mv.visitInsn(POP);

                                    // jump out if lyrics is empty
                                    mv.visitLabel(elseLabel);
                                }
                            }
                        }

                    };
                }

                return mv;
            }

        }, 0);

        return classWriter.toByteArray();
    }

}
