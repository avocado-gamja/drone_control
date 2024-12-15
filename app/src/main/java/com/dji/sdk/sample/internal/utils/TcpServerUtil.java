package com.dji.sdk.sample.internal.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class TcpServerUtil {

    private int port;
    private boolean isRunning = false;
    private TcpCommandListener commandListener;

    public TcpServerUtil(int port, TcpCommandListener commandListener) {
        this.port = port;
        this.commandListener = commandListener;
    }

    public void startServer() {
        isRunning = true;
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                while (isRunning) {
                    try (Socket clientSocket = serverSocket.accept();
                         BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                         PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                        // 클라이언트 명령 수신
                        String command = in.readLine();
                        if (command != null && commandListener != null) {
                            commandListener.onCommandReceived(command); // 명령 전달
                            out.println("Command '" + command + "' executed successfully.");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void stopServer() {
        isRunning = false;
    }

    // 명령 수신 콜백 인터페이스
    public interface TcpCommandListener {
        void onCommandReceived(String command);
    }
}
