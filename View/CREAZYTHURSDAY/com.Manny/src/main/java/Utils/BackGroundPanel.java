package Utils;

import javax.swing.*;
import java.awt.*;


public class BackGroundPanel extends JPanel {

    private Image backIcon;


    public BackGroundPanel(Image backIcon) {
        this.backIcon = backIcon;
        // this.backIcon 指的是"这个对象自己的 backIcon 字段"
        // 等号右边的 backIcon 指的是参数传入的值
        // 用 this 来区分两者
    }


    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(backIcon, 0, 0, this.getWidth(), this.getHeight(), null);
    }
}
