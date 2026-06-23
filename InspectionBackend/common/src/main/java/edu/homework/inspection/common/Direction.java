package edu.homework.inspection.common;

public enum Direction {
    U, D, L, R;

    /**
     * 根据位移计算车头朝向，匹配屏幕渲染坐标系。
     * 渲染层将 x（行号）映射到水平方向，y（列号）映射到垂直方向，
     * 因此 x 变化对应 L/R，y 变化对应 U/D。
     */
    public static Direction between(Point from, Point to) {
        if (to.getX() < from.getX()) {
            return L;
        }
        if (to.getX() > from.getX()) {
            return R;
        }
        if (to.getY() < from.getY()) {
            return U;
        }
        return D;
    }
}
