import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.ShrikeBTMethod;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.cha.CHACallGraph;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.config.AnalysisScopeReader;

import java.io.*;
import java.util.*;

public class test1 {

    static String[] path={"0-CMD","1-ALU","2-DataLog","3-BinaryHeap","4-NextDay","5-MoreTriangle"};
    static ArrayList<String> allMethods=new ArrayList<>();
    static ArrayList<String> allClasses=new ArrayList<>();
    static HashMap<String,String> son_father=new HashMap<>();

    public static void main(String[] args) throws ClassHierarchyException, IOException, CancelException, InvalidClassFileException {
        String beginPath="D:\\Projects\\IDEAPro\\wala2\\src\\main\\resources\\ClassicAutomatedTesting\\ClassicAutomatedTesting\\";
        String endPathTest = "\\target\\test-classes\\net\\mooctest";
        String endPathSrc = "\\target\\classes\\net\\mooctest";

        //itr为测试方法的指定
        int itr=0;

        AnalysisScope scope=getScope(itr,beginPath,endPathSrc,endPathTest,args[1]);
        //生成类层次
        ClassHierarchy cha = ClassHierarchyFactory.makeWithRoot(scope);
        //生成了所有application类的进入点
        Iterable<Entrypoint> eps = new AllApplicationEntrypoints(scope, cha);
        //读入所有类，将其继承关系存入son_father,将类存入allClasses
        for(IClass c:cha){
            String x = c.getName().toString();
            if(x.contains("Lnet/mooctest")){
                allClasses.add(x);
                IClass cf=c.getSuperclass();
                String cfName= cf.getName().toString();
                son_father.put(x,cfName);
            }
        }

        //类层次的调用图生成
        CHACallGraph cg = new CHACallGraph(cha);
        cg.init(eps);

        //将所有关注的方法取出放进allMethods
        for (CGNode node : cg) {
            if (node.getMethod() instanceof ShrikeBTMethod) {
                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                if ("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                    String classInnerName = method.getDeclaringClass().getName().toString();
                    String signature = method.getSignature();
                    if (classInnerName.substring(0, 13).equals("Lnet/mooctest")) {
                        allMethods.add(classInnerName + " " + signature);
                    }
                }
            }
        }

        ArrayList<String> methodNeedToTest = new ArrayList<>();
        ArrayList<String> classNeedToTest = new ArrayList<>();
        ArrayList<String> chosenClass=new ArrayList<>();
        ArrayList<String> chosenMethod = new ArrayList<>();

        //makeDotFile(cg);
        chooseClass(cg,classNeedToTest,chosenClass,beginPath,itr,args[2]);
        chooseMethod(cg,methodNeedToTest,chosenMethod,beginPath,itr,args[2]);

        if(args[0].equals("-c")){
            makeSelectFile("selection-class.txt",chosenClass);
        }else{
            makeSelectFile("selection-method.txt",chosenMethod);
        }

    }

    public static AnalysisScope getScope(int itr,String beginPath, String endPathSrc,String endPathTest,String realPath) throws IOException, InvalidClassFileException {
        //获取scope
        String scopePath = "scope.txt";
        File exPath = new File("exclusion.txt");
        ClassLoader classLoader = test1.class.getClassLoader();
        AnalysisScope scope = AnalysisScopeReader.readJavaScope(scopePath, exPath, classLoader);
        //完善scope
        String srcClassPath=realPath+"\\classes\\net\\mooctest";
        String testClassPath=realPath+"\\test-classes\\net\\mooctest";
        File[] srcFiles=new File(srcClassPath).listFiles();
        assert srcFiles != null;
        for(File f:srcFiles){
            scope.addClassFileToScope(ClassLoaderReference.Application, f);
        }
        File[] testFiles=new File(testClassPath).listFiles();
        assert testFiles != null;
        for(File f:testFiles){
            scope.addClassFileToScope(ClassLoaderReference.Application, f);
        }
        return scope;
    }

    //选择类粒度的测试方法
    public static void chooseClass(CHACallGraph cg,ArrayList<String> classNeedToTest,ArrayList<String> chosenClass,String beginPath,int itr,String changeInfoName) throws IOException, InvalidClassFileException {
        //先读出change_info的所有信息，放入classNeedToTest
        //File changeInfo=new File(beginPath+path[itr]+"\\data\\change_info.txt");
        File changeInfo=new File(changeInfoName);
        InputStreamReader read = new InputStreamReader(new FileInputStream(changeInfo),"UTF-8");
        BufferedReader bufferedReader = new BufferedReader(read);
        String info=null;
        while ((info=bufferedReader.readLine())!=null){
            String temp=info.substring(0,info.indexOf(' '));
            if(!classNeedToTest.contains(temp)){
                classNeedToTest.add(temp);
            }
        }
        //递归调用查找调用类的方法，找出所有需要测的方法和测他们的test
        int len=classNeedToTest.size();
        for(int i=0;i<len;i++){
            String className= classNeedToTest.get(i);
            getCalleeClass(cg,classNeedToTest,className);
        }
        //存储
        for(String methodName:allMethods){
            String className= methodName.substring(0,methodName.indexOf(' '));
            if(className.split("/")[2].contains("Test")){
                if(!methodName.contains("init")) {
                    for (String classNameChosen : classNeedToTest) {
                        if (className.equals(classNameChosen)) {
                            chosenClass.add(methodName);
                        }
                    }
                }
            }
        }
    }
    //递归获得全部改变和调用改变的class，包括testClass
    public static void getCalleeClass(CHACallGraph cg,ArrayList<String> classNeedToTest,String className) throws InvalidClassFileException {
        for (String methodName:allMethods){
            String methodClass=methodName.substring(0,methodName.indexOf(' '));
            if(className.equals(methodClass)){
                ArrayList<String> callee=new ArrayList<>();
                getCallee(cg,callee,methodName);
                for(String calleeName:callee){
                    String temp=calleeName.substring(0,calleeName.indexOf(' '));
                    if(!classNeedToTest.contains(temp)) {
                        classNeedToTest.add(temp);
                        getCalleeClass(cg,classNeedToTest,temp);
                    }
                }
            }
        }
    }

    //选择方法粒度的测试方法
    public static void chooseMethod(CHACallGraph cg,ArrayList<String> methodNeedToTest,ArrayList<String> chosenMethod,String beginPath,int itr,String changeInfoName) throws IOException, InvalidClassFileException {
        //将change_info读入methodNeedToTest
        //File changeInfo=new File(beginPath+path[itr]+"\\data\\change_info.txt");
        File changeInfo=new File(changeInfoName);
        InputStreamReader read = new InputStreamReader(new FileInputStream(changeInfo),"UTF-8");
        BufferedReader bufferedReader = new BufferedReader(read);
        String info=null;
        while ((info=bufferedReader.readLine())!=null){
            methodNeedToTest.add(info);
        }
        //递归调用查找调用方法的方法，找出所有需要测的方法和测他们的test方法
        int len=methodNeedToTest.size();
        for(int i=0;i<len;i++){
            String methodName= methodNeedToTest.get(i);
            getCalleeMethod(cg,methodNeedToTest,methodName);
        }
        //输出txt
        for(String sMethod:methodNeedToTest){
            if(sMethod.contains("Test")&&!sMethod.contains("init")){
                chosenMethod.add(sMethod);
            }
        }
    }

    public static void getCalleeMethod(CHACallGraph cg,ArrayList<String> methodNeedToTest,String  methodName) throws InvalidClassFileException {
        ArrayList<String> callee=new ArrayList<>();
        getCallee(cg,callee,methodName);
        for(String sMethod:callee){
            if(!methodNeedToTest.contains(sMethod)){
                methodNeedToTest.add(sMethod);
                getCalleeMethod(cg,methodNeedToTest,sMethod);
            }
        }
    }

    //获得全部的srcMethod方法的调用者,存入callee中
    public static void getCallee(CHACallGraph cg,ArrayList<String> callee,String srcMethod) throws InvalidClassFileException {
        String srcClassName=srcMethod.substring(0,srcMethod.indexOf(' '));
        if(!srcMethod.contains("init")&& son_father.containsValue(srcClassName)){
            for(String key:son_father.keySet()){
                if(son_father.get(key).equals(srcClassName)){
                    String sonMethodName=srcMethod.replaceAll(srcClassName.split("/")[2],key.split("/")[2]);
                    if(!allMethods.contains(sonMethodName)){
                        callee.add(sonMethodName);
                    }
                }
            }
        }
        for (CGNode node : cg) {
            // node中包含了很多信息，包括类加载器、方法信息等，这里只筛选出需要的信息
            if (node.getMethod() instanceof ShrikeBTMethod) {
                // node.getMethod()返回一个比较泛化的IMethod实例，不能获取到我们想要的信息
                // 一般地，本项目中所有和业务逻辑相关的方法都是ShrikeBTMethod对象
                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                // 使用Primordial类加载器加载的类都属于Java原生类，我们一般不关心。
                if ("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                    // 获取声明该方法的类的内部表示
                    String classInnerName = method.getDeclaringClass().getName().toString();
                    // 获取方法签名
                    String signature = method.getSignature();
                    //获得callsite，即所有该方法调用的方法
                    Collection<CallSiteReference> callSite=method.getCallSites();
                    //循环检查该方法是否调用了srcMethod的方法,将其存入callee
                    for(CallSiteReference callSiteReference:callSite){
                        String className= callSiteReference.getDeclaredTarget().getDeclaringClass().toString();
                        int classNamel=className.length();
                        className=className.substring(13,classNamel-1);
                        String[] tempClassName=className.split("/");
                        String methodName=callSiteReference.getDeclaredTarget().getSelector().toString();
                        methodName=tempClassName[0].substring(1)+"."+tempClassName[1]+"."+tempClassName[2]+"."+methodName;
                        String resName=className+ " " + methodName;
                        if(resName.equals(srcMethod)){
                            callee.add(classInnerName + " " + signature);
                        }
                    }

                }
            }
        }
    }

    //制作dot文件
    public static void makeDotFile(CHACallGraph cg) throws InvalidClassFileException, IOException {
        ArrayList<String> methodContent=new ArrayList<>();
        ArrayList<String> classContent=new ArrayList<>();
        methodContent.add("digraph cmd_method {\n");
        classContent.add("digraph cmd_class {\n");
        for(String methodName:allMethods){
            ArrayList<String> callee=new ArrayList<>();
            getCallee(cg,callee,methodName);
            if(callee.size()!=0){
                for(String calleeName:callee){
                    StringBuilder res=new StringBuilder();
                    res.append("\t\"").append(methodName.substring(methodName.indexOf(' ')+1)).append("\" -> \"").append(calleeName.substring(calleeName.indexOf(' ')+1)).append("\";\n");
                    String resS=res.toString();
                    if(!methodContent.contains(resS)){
                        methodContent.add(resS);
                    }
                    res=new StringBuilder();
                    res.append("\t\"").append(methodName.substring(0,methodName.indexOf(' '))).append("\" -> \"").append(calleeName.substring(0,calleeName.indexOf(' '))).append("\";\n");
                    resS=res.toString();
                    if(!classContent.contains(resS)){
                        classContent.add(resS);
                    }
                }
            }
        }
        methodContent.add("}");
        classContent.add("}");
        BufferedWriter out=new BufferedWriter(new FileWriter("method.dot"));
        StringBuilder res=new StringBuilder();
        for(String s:methodContent){
            res=res.append(s);
        }
        out.write(res.toString());
        out.close();
        out=new BufferedWriter((new FileWriter("class.dot")));
        res=new StringBuilder();
        for(String s:classContent){
            res=res.append(s);
        }
        out.write(res.toString());
        out.close();
    }
    //输出txt
    public static void makeSelectFile(String txtName, ArrayList<String> resList) throws FileNotFoundException {
        StringBuilder res = new StringBuilder();
        for (String s : resList) {
            res.append(s).append("\n");
        }
        File fp = new File(txtName);
        PrintWriter pfp = new PrintWriter(fp);
        pfp.print(res.toString());
        pfp.close();
    }
}
