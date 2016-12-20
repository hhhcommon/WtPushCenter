package com.woting;

public class OnlyTest {
    public static void main(String[] args) {
        String lineSeparator=java.security.AccessController.doPrivileged(new sun.security.action.GetPropertyAction("line.separator"));
        System.out.println(lineSeparator.length());
        System.out.println(lineSeparator);
        byte[] b=lineSeparator.getBytes();
        for (int i=0; i<b.length; i++) {
            System.out.println((b[i]));
        }

        String a="null::201::protocol=http:::4";
        String[] _split=a.split("::");
        String c=_split[2];
        int d=Integer.parseInt(_split[3]);
        System.out.println(c+"::"+d);
    }
}