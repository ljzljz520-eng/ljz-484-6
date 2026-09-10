package com.researchnotes;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 研究笔记发布服务入口。
 *
 * 用法：
 *   java -cp bin com.researchnotes.ResearchServer [端口] [数据目录] [前端目录]
 * 默认：端口 8080，数据目录 ./data，前端目录 ./web
 */
public final class ResearchServer {

    public static void main(String[] args) throws IOException {
        int port = 8080;
        Path dataDir = Paths.get("data");
        Path webDir = Paths.get("web");

        if (args.length >= 1 && !args[0].trim().isEmpty()) {
            try {
                port = Integer.parseInt(args[0].trim());
            } catch (NumberFormatException e) {
                System.err.println("端口号无效: " + args[0]);
                System.exit(1);
            }
        }
        if (args.length >= 2) {
            dataDir = Paths.get(args[1]);
        }
        if (args.length >= 3) {
            webDir = Paths.get(args[2]);
        }

        DataStore store = new DataStore(dataDir);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/", new HttpHandlers.ApiHandler(store));
        server.createContext("/", new HttpHandlers.StaticHandler(webDir));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("========================================================");
        System.out.println(" 研究笔记发布服务已启动");
        System.out.println(" 公开目录 : http://localhost:" + port + "/");
        System.out.println(" 研究员台 : http://localhost:" + port + "/admin");
        System.out.println(" 数据文件 : " + dataDir.toAbsolutePath().resolve("db.json"));
        System.out.println(" 停止服务 : 按 Ctrl+C");
        System.out.println("========================================================");
    }
}
