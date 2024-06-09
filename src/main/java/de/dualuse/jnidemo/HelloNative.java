package de.dualuse.jnidemo;

public class HelloNative {

    static final JNILibrary lib = JNILibrary.load("JniDemo");

    static native public void sayHello(int times);


    public static void main(String[] args) {

        sayHello( 3 );

    }
}
