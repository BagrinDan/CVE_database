package org.example;

import org.example.controller.SmartProxy;
import org.example.controller.Proxy;


public class Main {
    public static void main(String[] args) {
        System.out.print("Version_0.1 *** Tuki-tuki ***! \n");

        SmartProxy proxy = new SmartProxy();
        proxy.run();
    }
}