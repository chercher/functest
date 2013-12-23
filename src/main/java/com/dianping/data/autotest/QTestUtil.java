package com.dianping.data.autotest;

import org.apache.hadoop.hive.cli.CliDriver;
import org.apache.hadoop.hive.cli.CliSessionState;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.ql.Driver;
import org.apache.hadoop.hive.ql.exec.Utilities.StreamPrinter;
import org.apache.hadoop.hive.ql.session.SessionState;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by root on 13-12-5.
 */
public class QTestUtil {

    private static String queryDir;
    private static String expDir;
    private static String outDir;
    private static String diffDir;
    private static String logDir;
    private File expf;
    private File outf;
    private FileOutputStream outfo;
    private File diff;
    private FileOutputStream diffo;
    private File logf;
    private FileOutputStream logfo;
    private static HiveConf conf;
    private static CliSessionState ss;
    private static CliDriver cliDriver;


    public QTestUtil(String queryDir, String expDir, String outDir, String diffDir, String logDir) throws Exception {

        this.queryDir = queryDir;
        this.expDir = expDir;
        this.outDir = outDir;
        this.diffDir = diffDir;
        this.logDir = logDir;

        preCheckDirs(queryDir, expDir, outDir, diffDir, logDir);

    }

    private void preCheckDirs(String queryDir, String expDir, String outDir, String diffDir, String logDir) throws Exception {

        File queryPath = new File(queryDir);
        if (!queryPath.exists() || !queryPath.isDirectory()) {
            System.out.println("ERROR: queryDir is missing.");
            System.exit(1);
        }
        
        File expPath = new File(expDir);
        if (!expPath.exists() || !expPath.isDirectory()) {
            System.out.println("ERROR: expDir is missing.");
            System.exit(1);
        }

        File outPath = new File(outDir);
        if (!outPath.exists() || !outPath.isDirectory()) {
            outPath.mkdir();
            System.out.println("WARNING: outDir is missing, and it's been created.");
        }

        File diffPath = new File(diffDir);
        if (!diffPath.exists() || !diffPath.isDirectory()) {
            diffPath.mkdir();
            System.out.println("WARNING: diffDir is missing, and it's been created.");
        }

        File logPath = new File(logDir);
        if (!logPath.exists() || !logPath.isDirectory()) {
            logPath.mkdir();
            System.out.println("WARNING: diffDir is missing, and it's been created.");
        }

        File[] qfiles = new File(queryDir).listFiles();
        int qfileNum = qfiles.length;
        for (int i = 0; i < qfileNum; i++) {
            String qnamefull = qfiles[i].getName();
            boolean dotq = qnamefull.endsWith(".q");
            if (!dotq) {
                System.out.println("ERROR: " + qnamefull + " is not end with .q");
                System.exit(1);
            }
            String qname = qnamefull.substring(0, qnamefull.indexOf("."));
            File expFile = new File(expDir, qname + ".exp");
            if (!expFile.exists()) {
                System.out.println("ERROR: " + qname + ".exp" + " is missing.");
                System.exit(1);
            }
        }

    }

    private File newFile(String dir, String fileName, String suffix) {

        return new File(dir + File.separator + fileName + suffix);
    }

    public static void initConf() throws Exception {

        conf = new HiveConf(Driver.class);
        conf.addResource("core-site.xml");
        conf.addResource("hdfs-site.xml");
        conf.addResource("mapred-site.xml");
//        conf.addResource("hive-site.xml");
        System.out.println(conf.get("javax.jdo.option.ConnectionURL"));

        ss = new CliSessionState(conf);
        ss.err = System.out;

        ss.initFiles.add(System.getenv("HIVE_CONF_DIR") + File.separator + ".hiverc");
        SessionState.start(ss);

        cliDriver = new CliDriver();
        cliDriver.processInitFiles(ss);

    }

    public void cliInit(String qname) throws Exception {

        SessionState.start(ss);

        outf = newFile(outDir, qname, ".out");
        outf.createNewFile();
        outfo = new FileOutputStream(outf);

        logf = newFile(logDir, qname, ".log");
        logf.createNewFile();
        logfo = new FileOutputStream(logf);

        ss.out = new PrintStream(outfo, true, "UTF-8");
        ss.err = new PrintStream(logfo, true, "UTF-8");


    }

    public int executeClient(String qname) throws Exception {

        File qfile = newFile(queryDir, qname, ".q");
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(qfile)));
        StringBuffer sb = new StringBuffer();
        String line = br.readLine();
        while (line != null) {
            sb.append(line);
            sb.append("\n");
            line = br.readLine();
        }
        br.close();
        String cmds = sb.toString();

        int result = cliDriver.processLine(cmds);
        return result;

    }

    public int executeDiffCmd(String outf, String expf, OutputStream diffo) throws Exception {

        int result = 0;
        List<String> diffCmdArgs = new ArrayList<String>();
        diffCmdArgs.add("diff");
        diffCmdArgs.add("-a");
        diffCmdArgs.add("-b");
        diffCmdArgs.add("-B");
        diffCmdArgs.add(outf);
        diffCmdArgs.add(expf);
        String[] cmdArray = (String[])diffCmdArgs.toArray(new String[diffCmdArgs.size()]);

//        System.setOut(new PrintStream(diff, true, "UTF-8"));

        PrintStream out = new PrintStream(diffo, true, "UTF-8");
        PrintStream err = new PrintStream(diffo, true, "UTF-8");

        Process executor = Runtime.getRuntime().exec(cmdArray);

        StreamPrinter outPrinter = new StreamPrinter(executor.getInputStream(), null, out);
        StreamPrinter errPrinter = new StreamPrinter(executor.getErrorStream(), null, err);

        outPrinter.start();
        errPrinter.start();

        result = executor.waitFor();

        outPrinter.join();
        errPrinter.join();

        out.close();
        err.close();

        return result;
    }

    public int checkCliDriverResults(String qname) throws Exception {

        expf = newFile(expDir, qname, ".exp");
        diff = newFile(diffDir, qname, ".diff");
        diff.createNewFile();
        diffo = new FileOutputStream(diff);
        int diffVal = executeDiffCmd(outf.getPath(), expf.getPath(), diffo);
        return diffVal;
    }

    public static List<String> queryListRunnerSingleThreaded(QTestUtil[] qt) throws Exception {
        
        List<String> failedCases = new ArrayList<String>();
        File[] qfiles = new File(qt[0].queryDir).listFiles();
        for (int i = 0; i < qt.length; i++) {
            String qnamefull = qfiles[i].getName();
            String qname = qnamefull.substring(0, qnamefull.indexOf("."));
            qt[i].cliInit(qname);
            qt[i].executeClient(qname);
            int ecode = qt[i].checkCliDriverResults(qname);
            if (ecode != 0) {
                failedCases.add(qname + ".q");
                System.err.println("Test " + qfiles[i].getName()
                        + "results check failed.");
            }
        }
        return failedCases;
    }

    public static class QTRunner implements Runnable {
        private QTestUtil qt;
        private String qname;

        public QTRunner(QTestUtil qt, String qname) {
            this.qt = qt;
            this.qname = qname;
        }

        @Override
        public void run() {
            try {
                qt.cliInit(qname);
                qt.executeClient(qname);
            } catch (Exception e) {
                System.err.println("Query file " + qname + " failed with exception " + e.getMessage());
                e.printStackTrace();
            }

        }
    }

    public static int queryListRunnerMultiThreaded(QTestUtil[] qt) throws Exception {
        int failedCount = 0;

        File[] qfiles = new File(qt[0].queryDir).listFiles();
        QTRunner[] qtRunners = new QTRunner[qfiles.length];
        Thread[] qtThreads = new Thread[qfiles.length];
        String[] qnames = new String[qfiles.length];

        for (int i = 0; i < qfiles.length; i++) {
            String qnamefull = qfiles[i].getName();
            qnames[i] = qnamefull.substring(0, qnamefull.indexOf("."));
            qtRunners[i] = new QTRunner(qt[i], qnames[i]);
            qtThreads[i] = new Thread(qtRunners[i]);
        }

        for (int i = 0; i < qfiles.length; i++) {
            qtThreads[i].start();
        }

        for (int i = 0; i < qfiles.length; i++) {
            qtThreads[i].join();
            int ecode = qt[i].checkCliDriverResults(qnames[i]);
            if (ecode != 0) {
                failedCount += 1;
                System.err.println("Test " + qnames[i] + " results check failed.");
            }
        }

        return failedCount;
    }

    public static void genTestReport(int qfileNum, List<String> failedCases) {

        System.out.println("Results:");
        if (failedCases.size() != 0) {
            System.out.println("Tests run: " + qfileNum + ", " + "Failures: " + failedCases.size());
            System.out.print("Failed cases: ");
            for (String s: failedCases) {
                System.out.print(s + " ");
            }
            System.out.println();
        } else {
            System.out.println("Tests run: " + qfileNum + ", " + "Failures: " + 0);
        }
    }

    public static void main(String[] args) throws Exception {

        String queryDir = "queries";
        String expDir = "expects";
        String outDir = "results";
        String diffDir = "diffs";
        String logDir = "logs";

        int qfileNum = new File(queryDir).listFiles().length;
        QTestUtil[] qt = new QTestUtil[qfileNum];
        for (int i = 0; i< qfileNum; i++) {
            qt[i] = new QTestUtil(queryDir, expDir, outDir, diffDir, logDir);
        }

        QTestUtil.initConf();

        List<String> failedCases = QTestUtil.queryListRunnerSingleThreaded(qt);

        QTestUtil.genTestReport(qfileNum, failedCases);

    }
}
