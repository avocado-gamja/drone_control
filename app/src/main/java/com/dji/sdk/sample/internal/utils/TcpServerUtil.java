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
                System.out.println("TCP Server started on port: " + port);
                while (isRunning) {
                    try {
                        System.out.println("Waiting for a client...");
                        ToastUtils.setResultToToast("Waiting for a client on port : " + port);
                        Socket clientSocket = serverSocket.accept();
                        System.out.println("Client connected: " + clientSocket.getInetAddress().getHostAddress());
                        ToastUtils.setResultToToast("Client connected: " + clientSocket.getInetAddress().getHostAddress());

                        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                            // 클라이언트 명령 수신
                            String command = in.readLine();
                            if (command != null) {
                                System.out.println("Received command: " + command);
                                ToastUtils.setResultToToast("Command: " + command);
                                if (commandListener != null) {
                                    commandListener.onCommandReceived(command);
                                }

                                // 응답 전송
                                String response = "Command '" + command + "' executed successfully.";
                                out.println(response);
                                System.out.println("Sent response: " + response);
                                ToastUtils.setResultToToast("Response sent: " + response);
                            }
                        } catch (Exception e) {
                            System.err.println("Error handling client connection: " + e.getMessage());
                            ToastUtils.setResultToToast("Error handling client connection: " + e.getMessage());
                        }
                    } catch (Exception e) {
                        System.err.println("Error in server loop: " + e.getMessage());
                        ToastUtils.setResultToToast("Error in server loop: " + e.getMessage());
                    }
                }

            } catch (Exception e) {
                System.err.println("Error starting TCP Server: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    public void stopServer() {
        isRunning = false;
        System.out.println("TCP Server stopped.");
    }

    // 명령 수신 콜백 인터페이스
    public interface TcpCommandListener {
        void onCommandReceived(String command);
    }
}
