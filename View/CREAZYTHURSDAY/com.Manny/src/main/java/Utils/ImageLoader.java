package Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * ============================================================
 * 【图片加载工具 —— ImageLoader】
 * ============================================================
 *
 * 【这个类是干什么的？】
 * 程序运行需要加载各种图片资源（小车图标、logo、背景图等）。
 * 开发时这些图片在文件系统的 /images/ 目录下，
 * 但打包成 JAR 文件后，它们被压缩在 JAR 包内部，不能再用文件路径访问。
 *
 * ImageLoader 解决了这个问题——它优先从 classpath（JAR 内部）加载，
 * 这样无论是开发环境还是打包后运行，都能正确找到图片。
 *
 * 【什么是 classpath？】
 * classpath 是 Java 程序查找 .class 文件和资源文件的"搜索路径"。
 * 就像 Windows 的 PATH 环境变量——系统只在 PATH 里的目录找你输入的命令。
 * Java 只在 classpath 指定的位置找它需要的文件。
 *
 * JAR 包本质上就是一个 zip 压缩包，classpath 可以指向 JAR 包的内部。
 *
 * 【类比理解】
 * 就像你手机相册里的照片：
 * - 手机没插 SD 卡时（开发环境），照片在手机内存里
 * - 插了 SD 卡后（JAR 打包），照片移到了 SD 卡
 * - 但相册 App 不管照片在哪，它总是能找到并显示
 * - ImageLoader 就是帮程序"不管图片在哪都能找到"的工具
 */
public class ImageLoader {
    private static final Logger log = LoggerFactory.getLogger(ImageLoader.class);


    private ImageLoader() {}


    public static BufferedImage load(String filename) {
        // 拼接资源路径：所有图片统一放在 classpath 的 /images/ 目录下
        String resourcePath = "/images/" + filename;

        // try-with-resources：确保 InputStream 使用完毕后自动关闭
        // getResourceAsStream() 从 classpath 中读取资源文件
        try (InputStream in = ImageLoader.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                // ImageIO.read() 将输入流解析为 BufferedImage 对象
                BufferedImage img = ImageIO.read(in);
                log.debug("ImageLoader: 从 classpath 加载 {}", resourcePath);
                return img;
            }
        } catch (Exception e) {
            log.warn("ImageLoader: classpath 加载 {} 失败: {}", resourcePath, e.getMessage());
        }

        log.error("ImageLoader: 无法加载图片 {}", resourcePath);
        return null;
    }
}
