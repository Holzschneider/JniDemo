#include "de_dualuse_jnidemo_HelloNative.h"

#include <stdio.h>


JNIEXPORT void JNICALL Java_de_dualuse_jnidemo_HelloNative_sayHello
  (JNIEnv *env, jclass clazz, jint n) {
    // This is the native method that we want to call from Java
    for (int i=0;i<n;i++)
        printf("Hello from C++!\n");

    fflush(stdout);
}