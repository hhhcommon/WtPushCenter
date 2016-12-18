package com.woting;

public class OnlyTest {
    public static void main(String[] args) {
        String lineSeparator = java.security.AccessController.doPrivileged(new sun.security.action.GetPropertyAction("line.separator"));
        System.out.println(lineSeparator.length());
        System.out.println(lineSeparator);
        byte[] b=lineSeparator.getBytes();
        for (int i=0; i<b.length; i++) {
            System.out.println((b[i]));
        }
    }
}