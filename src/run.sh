rm -f resources/res/*.*
rm -f com/intel/qat/libqat-java.so
gcc -shared -std=c11 -pedantic-errors -fPIC -I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux -lqatzip -lnuma -lpthread jni/com_intel_qat_InternalJNI.c -o libqat-java.so
mv -f libqat-java.so com/intel/qat/
javac com/intel/qat/Example.java
java -cp . -Djava.library.path=com/intel/qat com/intel/qat/Example ./resources/ ./resources/res/ 11
