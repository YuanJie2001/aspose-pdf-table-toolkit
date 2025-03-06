package com.vector;

import javassist.*;

import java.io.IOException;

/**
 * @author YuanJie
 * @ClassName com.vector.Main
 * @description: TODO
 * @date 2025/2/27 11:32
 */
public class CrackAspose {
    private static final String originPath = "C:\\Users\\YuanJie\\Desktop\\aspose-wps\\lib\\aspose-words-24.3-jdk17-crack.jar";
    public static void main(String[] args) throws NotFoundException, CannotCompileException, IOException {
        ClassPool.getDefault().insertClassPath(originPath);
        CtClass clazz = ClassPool.getDefault().getCtClass("com.aspose.words.zzYzt");
        CtMethod[] ctMethods = clazz.getDeclaredMethods();
        for (CtMethod ctMethod : ctMethods) {
            CtClass[] ps = ctMethod.getParameterTypes();
            if(ps.length == 4 && "zzWdb".equals(ctMethod.getName()) && ps[0].isArray() && ps[1].isArray() && ps[2].isArray() && ps[3].isArray() &&
            ps[0].getComponentType() == CtClass.byteType && ps[1].getComponentType() == CtClass.byteType && ps[2].getComponentType() == CtClass.byteType && ps[3].getComponentType() == CtClass.byteType){
                ctMethod.setBody("return true;");
            }
        }
        clazz.writeFile();

        clazz = ClassPool.getDefault().getCtClass("com.aspose.words.zz2O");
        ctMethods = clazz.getDeclaredMethods();
        for (CtMethod ctMethod : ctMethods) {
            CtClass[] ps = ctMethod.getParameterTypes();
            if(ps.length==0&&ctMethod.getName().equals("zzWlq")){
                ctMethod.setBody("return 256;");
            }
        }
        clazz.writeFile();

        clazz = ClassPool.getDefault().getCtClass("com.aspose.words.zzL7");
        ctMethods = clazz.getDeclaredMethods();
        for (CtMethod ctMethod : ctMethods) {
            if(ctMethod.getName().equals("zzX30")){
                ctMethod.setBody("{if (zzXbU == 0L) {zzXbU ^= zzW7B;} return com.aspose.words.zzW4v.zzZ4h;}");
            }
        }
        clazz.writeFile();
    }
}
