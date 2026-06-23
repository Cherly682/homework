package edu.homework.inspection.launcher;

import edu.homework.inspection.common.RabbitProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 统一启动器——以独立进程方式启动 Controller、Navigator、Car、Recorder 四个组件。
 * 各组件编译为独立的 fat JAR，通过 {@code java -jar} 在各自 JVM 中运行。
 */
public class LauncherMain {
    private static final Logger log = LoggerFactory.getLogger(LauncherMain.class);

    private static final String[] MODULE_NAMES = {"controller", "navigator", "car", "recorder"};
    private static final List<Process> CHILD_PROCESSES = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();

        // 声明 RabbitMQ 拓扑（幂等，确保子进程启动前队列/交换机就绪）
        try (com.rabbitmq.client.Channel channel = RabbitProvider.channel()) {
            RabbitProvider.declareTopology(channel);
        }
        log.info("RabbitMQ topology declared");

        // 计算 JAR 目录的优先级：
        // 1. 启动参数传入
        // 2. APP_HOME 环境变量
        // 3. 从 launcher 自身 JAR 的位置推算（launcher/target/launcher.jar 的上两级）
        // 4. 当前工作目录
        File jarDir = resolveJarDir(args);
        log.info("JAR directory: {}", jarDir.getAbsolutePath());

        // 依次以独立进程启动四个模块
        for (String name : MODULE_NAMES) {
            File jarFile = new File(jarDir, name + File.separator + "target" + File.separator + name + ".jar");
            if (!jarFile.exists()) {
                log.error("JAR not found: {}, skip launching {}", jarFile.getAbsolutePath(), name);
            } else {
                startModule(jarFile, jarDir);
            }
            // 短暂间隔避免瞬时资源争抢
            Thread.sleep(500);
        }

        // 注册 JVM 关闭钩子——优雅终止所有子进程
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Launcher shutting down, stopping child processes...");
            for (Process p : CHILD_PROCESSES) {
                if (p.isAlive()) {
                    p.destroy();
                }
            }
        }));

        log.info("All components started in {}ms", (System.nanoTime() - t0) / 1_000_000L);
        log.info("Controller/Navigator/Car/Recorder running as independent processes");
        new CountDownLatch(1).await();
    }

    /**
     * 解析 JAR 所在目录，按优先级：命令行参数 → APP_HOME 环境变量 → launcher JAR 位置推算 → user.dir
     */
    private static File resolveJarDir(String[] args) {
        // 1. 命令行参数
        if (args.length > 0 && args[0] != null && !args[0].isBlank()) {
            File dir = new File(args[0]);
            if (dir.isDirectory()) return dir;
        }
        // 2. APP_HOME 环境变量
        String appHome = System.getenv("APP_HOME");
        if (appHome != null && !appHome.trim().isEmpty()) {
            File dir = new File(appHome.trim());
            if (dir.isDirectory()) return dir;
        }
        // 3. 从 launcher 自身 JAR 位置推算
        try {
            String jarPath = LauncherMain.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI().getPath();
            File jarFile = new File(jarPath);
            if (jarFile.isFile()) {
                // launcher/target/launcher.jar → target 目录
                File targetDir = jarFile.getParentFile();
                // target 目录的父目录就是 InspectionBackend 根目录
                if (targetDir != null && targetDir.getParentFile() != null) {
                    return targetDir.getParentFile();
                }
            }
        } catch (URISyntaxException ignored) {
        }
        // 4. 回退到当前工作目录
        return new File(System.getProperty("user.dir"));
    }

    private static void startModule(File jarFile, File jarDir) throws Exception {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";

        ProcessBuilder pb = new ProcessBuilder(
                javaBin, "-jar", jarFile.getAbsolutePath()
        );
        pb.directory(jarDir);
        pb.inheritIO();
        pb.environment().put("APP_HOME", jarDir.getAbsolutePath());

        Process process = pb.start();
        CHILD_PROCESSES.add(process);
        log.info("Started {} (pid={})", jarFile.getName(), process.pid());
    }
}
