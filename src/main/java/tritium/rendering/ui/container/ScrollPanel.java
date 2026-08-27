package tritium.rendering.ui.container;

import lombok.Getter;
import org.lwjgl.input.Keyboard;
import tritium.rendering.Rect;
import tritium.rendering.StencilClipManager;
import tritium.rendering.animation.Interpolations;
import tritium.rendering.ui.AbstractWidget;

import java.util.List;

/**
 * 一个从上至下排列的可以滚动的容器
 * @author IzumiiKonata
 * Date: 2025/7/8 21:45
 */
public class ScrollPanel extends AbstractWidget<ScrollPanel> {

    @Getter
    private double spacing = 0;
    private double horizontalSpacing = 0;
    private double verticalSpacing = 0;
    private double contentPaddingLeft = 0;
    private double contentPaddingTop = 0;
    private double contentPaddingRight = 0;
    private double contentPaddingBottom = 0;
    public double actualScrollOffset = 0, targetScrollOffset = 0;

    @Getter
    private Alignment alignment = Alignment.VERTICAL;

    /**
     * 子组件的排列方式
     */
    public enum Alignment {
        /**
         * 仅垂直排列 (默认)
         */
        VERTICAL,
        /**
         * 仅水平排列
         */
        HORIZONTAL,
        /**
         * 垂直排列并水平填充
         */
        VERTICAL_WITH_HORIZONTAL_FILL
    }

    public ScrollPanel setAlignment(Alignment alignment) {

        if (alignment == null)
            throw new IllegalArgumentException("Alignment cannot be null!");

        this.alignment = alignment;
        return this;
    }

    @Override
    public void onRender(double mouseX, double mouseY) {
        this.actualScrollOffset = Interpolations.interpolate(this.actualScrollOffset, this.targetScrollOffset, 1f);

        // 设置子组件的垂直位置
        this.alignChildren();
    }

    @Override
    protected void renderDebugLayout() {
        super.renderDebugLayout();
        Rect.draw(this.getX(), this.getY(), this.getWidth(), this.getHeight(), reAlpha(0x00F50EE8, this.getAlpha() * .05f));
    }

    public ScrollPanel setSpacing(double spacing) {
        this.spacing = spacing;
        this.horizontalSpacing = spacing;
        this.verticalSpacing = spacing;
        return this;
    }

    public ScrollPanel setHorizontalSpacing(double horizontalSpacing) {
        this.horizontalSpacing = horizontalSpacing;
        return this;
    }

    public ScrollPanel setVerticalSpacing(double verticalSpacing) {
        this.verticalSpacing = verticalSpacing;
        return this;
    }

    public ScrollPanel setContentPadding(double padding) {
        this.contentPaddingLeft = padding;
        this.contentPaddingTop = padding;
        this.contentPaddingRight = padding;
        this.contentPaddingBottom = padding;
        return this;
    }

    @Override
    public boolean onDWheel(double mouseX, double mouseY, int dWheel) {

        // 垂直滑动
        this.performScroll(dWheel);
        super.onDWheel(mouseX, mouseY, dWheel);

        return true;
    }

    private void performScroll(int dWheel) {

        if (dWheel != 0) {

            double strength = 12;

            if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT))
                strength *= 2;

            if (dWheel > 0)
                this.targetScrollOffset -= strength;
            else
                this.targetScrollOffset += strength;
        }

        this.targetScrollOffset = Math.max(this.targetScrollOffset, 0);

        switch (this.alignment) {
            case VERTICAL -> {
                double childrenHeightSum = this.getChildrenHeightSum();
                if (childrenHeightSum > this.getHeight())
                    this.targetScrollOffset = Math.min(this.targetScrollOffset, childrenHeightSum - this.getHeight());
                else
                    this.targetScrollOffset = Math.min(this.targetScrollOffset, 0);
            }
            case HORIZONTAL -> {
                double childrenWidthSum = this.getChildrenWidthSum();
                if (childrenWidthSum > this.getWidth())
                    this.targetScrollOffset = Math.min(this.targetScrollOffset, childrenWidthSum - this.getWidth());
                else
                    this.targetScrollOffset = Math.min(this.targetScrollOffset, 0);
            }
            case VERTICAL_WITH_HORIZONTAL_FILL -> {
                double childrenHeightSum = this.getChildrenHeightSumHorizontalFill();
                if (childrenHeightSum > this.getHeight())
                    this.targetScrollOffset = Math.min(this.targetScrollOffset, childrenHeightSum - this.getHeight());
                else
                    this.targetScrollOffset = Math.min(this.targetScrollOffset, 0);
            }
        }
    }

    public void scrollToEnd() {
        switch (this.alignment) {
            case VERTICAL -> {
                double childrenHeightSum = this.getChildrenHeightSum();
                if (childrenHeightSum > this.getHeight())
                    this.targetScrollOffset = childrenHeightSum - this.getHeight();
                else
                    this.targetScrollOffset = 0;
            }
            case HORIZONTAL -> {
                double childrenWidthSum = this.getChildrenWidthSum();
                if (childrenWidthSum > this.getWidth())
                    this.targetScrollOffset = childrenWidthSum - this.getWidth();
                else
                    this.targetScrollOffset = 0;
            }
            case VERTICAL_WITH_HORIZONTAL_FILL -> {
                double childrenHeightSum = this.getChildrenHeightSumHorizontalFill();
                if (childrenHeightSum > this.getHeight())
                    this.targetScrollOffset = childrenHeightSum - this.getHeight();
                else
                    this.targetScrollOffset = 0;
            }
        }
    }

    public void scrollToEndImmediately() {
        switch (this.alignment) {
            case VERTICAL -> {
                double childrenHeightSum = this.getChildrenHeightSum();
                if (childrenHeightSum > this.getHeight())
                    this.actualScrollOffset = childrenHeightSum - this.getHeight();
                else
                    this.actualScrollOffset = 0;
            }
            case HORIZONTAL -> {
                double childrenWidthSum = this.getChildrenWidthSum();
                if (childrenWidthSum > this.getWidth())
                    this.actualScrollOffset = childrenWidthSum - this.getWidth();
                else
                    this.actualScrollOffset = 0;
            }
            case VERTICAL_WITH_HORIZONTAL_FILL -> {
                double childrenHeightSum = this.getChildrenHeightSumHorizontalFill();
                if (childrenHeightSum > this.getHeight())
                    this.actualScrollOffset = childrenHeightSum - this.getHeight();
                else
                    this.actualScrollOffset = 0;
            }
        }
    }

    public boolean isScrolledToEnd() {
        return switch (this.alignment) {
            case VERTICAL -> {
                double childrenHeightSum = this.getChildrenHeightSum();
                if (childrenHeightSum > this.getHeight())
                    yield this.targetScrollOffset >= childrenHeightSum - this.getHeight();
                else
                    yield this.targetScrollOffset == 0;
            }
            case HORIZONTAL -> {
                double childrenWidthSum = this.getChildrenWidthSum();
                if (childrenWidthSum > this.getWidth())
                    yield this.targetScrollOffset >= childrenWidthSum - this.getWidth();
                else
                    yield this.targetScrollOffset == 0;
            }
            case VERTICAL_WITH_HORIZONTAL_FILL -> {
                double childrenHeightSum = this.getChildrenHeightSumHorizontalFill();
                if (childrenHeightSum > this.getHeight())
                    yield this.targetScrollOffset >= childrenHeightSum - this.getHeight();
                else
                    yield this.targetScrollOffset == 0;
            }
        };
    }

    public void alignChildren() {
        double offsetX = contentPaddingLeft;
        double offsetY = contentPaddingTop;
        double rowHeight = 0;

        if (this.alignment == Alignment.VERTICAL || this.alignment == Alignment.VERTICAL_WITH_HORIZONTAL_FILL)
            offsetY = -this.actualScrollOffset;
        else
            offsetX = -this.actualScrollOffset;

        switch (this.alignment) {
            case VERTICAL -> {
                double childrenHeightSum = this.getChildrenHeightSum();

                if (childrenHeightSum < this.getHeight())
                    this.targetScrollOffset = 0;
                else if (this.targetScrollOffset > childrenHeightSum - this.getHeight()) {
                    this.targetScrollOffset = childrenHeightSum - this.getHeight();
                }
            }
            case HORIZONTAL -> {
                double childrenWidthSum = this.getChildrenWidthSum();

                if (childrenWidthSum < this.getWidth())
                    this.targetScrollOffset = 0;
                else if (this.targetScrollOffset > childrenWidthSum - this.getWidth()) {
                    this.targetScrollOffset = childrenWidthSum - this.getWidth();
                }
            }
            case VERTICAL_WITH_HORIZONTAL_FILL -> {
                double childrenHeightSum = this.getChildrenHeightSumHorizontalFill();

                if (childrenHeightSum < this.getHeight())
                    this.targetScrollOffset = 0;
                else if (this.targetScrollOffset > childrenHeightSum - this.getHeight()) {
                    this.targetScrollOffset = childrenHeightSum - this.getHeight();
                }
            }
        }

        for (AbstractWidget<?> child : this.getChildren()) {

            double width = child.getWidth();
            double height = child.getHeight();

            if (child.isHidden())
                continue;

            switch (this.alignment) {
                case VERTICAL -> {
                    child.setPosition(child.getRelativeX(), offsetY);
                    offsetY += height + verticalSpacing;
                }
                case HORIZONTAL -> {
                    child.setPosition(offsetX, child.getRelativeY());
                    offsetX += width + horizontalSpacing;
                }
                case VERTICAL_WITH_HORIZONTAL_FILL -> {
                    if (offsetX > contentPaddingLeft && offsetX + width > this.getWidth() - contentPaddingRight) {
                        offsetX = contentPaddingLeft;
                        offsetY += rowHeight + verticalSpacing;
                        rowHeight = 0;
                    }

                    child.setPosition(offsetX, offsetY);
                    offsetX += width + horizontalSpacing;
                    rowHeight = Math.max(rowHeight, height);
                }
            }
        }
    }

    protected double getChildrenHeightSum() {
        double result = 0;

        List<AbstractWidget<?>> children = this.getChildren();

        for (AbstractWidget<?> child : children) {

            double width = child.getWidth();
            double height = child.getHeight();

            if (child.isHidden())
                continue;

            result += height + this.verticalSpacing;
        }

        if (result > 0)
            result -= this.verticalSpacing;

        return result;
    }

    protected double getChildrenWidthSum() {
        double result = 0;

        List<AbstractWidget<?>> children = this.getChildren();

        for (AbstractWidget<?> child : children) {

            double width = child.getWidth();
            double height = child.getHeight();

            if (child.isHidden())
                continue;

            result += width + this.horizontalSpacing;
        }

        if (result > 0)
            result -= this.horizontalSpacing;

        return result;
    }

    protected double getChildrenHeightSumHorizontalFill() {
        double result = contentPaddingTop + contentPaddingBottom;
        double offsetX = contentPaddingLeft;
        double rowHeight = 0;
        boolean hasRow = false;

        List<AbstractWidget<?>> children = this.getChildren();

        for (AbstractWidget<?> child : children) {

            double width = child.getWidth();
            double height = child.getHeight();

            if (child.isHidden())
                continue;

            if (offsetX > contentPaddingLeft && offsetX + width > this.getWidth() - contentPaddingRight) {
                result += rowHeight + verticalSpacing;
                offsetX = contentPaddingLeft;
                rowHeight = 0;
            }

            offsetX += width + horizontalSpacing;
            rowHeight = Math.max(rowHeight, height);
            hasRow = true;
        }

        if (hasRow)
            result += rowHeight;

        return result;
    }

    @Override
    public void renderWidget(double mouseX, double mouseY, int dWheel) {
        StencilClipManager.beginClip(() -> Rect.draw(this.getX(), this.getY(), this.getWidth(), this.getHeight(), -1));

        super.renderWidget(mouseX, mouseY, dWheel);

        StencilClipManager.endClip();
    }

    @Override
    protected boolean shouldRenderChildren(AbstractWidget<?> child, double mouseX, double mouseY) {

        switch (this.getAlignment()) {
            case VERTICAL, VERTICAL_WITH_HORIZONTAL_FILL -> {
                if (child.getRelativeY() + child.getHeight() < 0)
                    return false;

                if (child.getRelativeY() > this.getHeight())
                    return false;
            }
            case HORIZONTAL -> {
                if (child.getRelativeX() + child.getWidth() < 0)
                    return false;

                if (child.getRelativeX() > this.getWidth())
                    return false;
            }
        }

        return true;
    }

    @Override
    protected boolean shouldClickChildren(double mouseX, double mouseY) {
//        System.out.println(this.testHovered(mouseX, mouseY));
        return this.testHovered(mouseX, mouseY);
    }

    @Override
    public boolean canBeScrolled() {
        return true;
    }
}
