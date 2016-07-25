package com.woting;

public class TestRunStart {

    public static void main(String[] args) {
        System.out.println("3333");
        TestRun tr=new TestRun();
        tr.run();
        tr.start();
    }
}

class TestRun implements Runnable {
    public void start() {
        System.out.println("onStart::====END");
    }

    @Override
    public void run() {
        int i=0;
        while (true) {
            synchronized (this) {
                System.out.println("onRun::===="+(i++));
                if (i>10) break;
                try { this.wait(400); } catch(Exception e) {}
            }
        }
        
    }
    
}