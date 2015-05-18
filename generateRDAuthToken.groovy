#!/usr/bin/env groovy

import com.urbancode.commons.util.processes.Processes
import com.urbancode.shell.Shell;

final String lineSep = System.getProperty('line.separator')
final String dirSep = File.separatorChar
final String osName = System.getProperty('os.name').toLowerCase(Locale.US)
final String pathSep = System.getProperty('path.separator')

final boolean windows = (osName =~ /windows/)
final boolean vms = (osName =~ /vms/)
final boolean os9 = (osName =~ /mac/ && !osName.endsWith('x'))
final boolean unix = (pathSep == ':' && !vms && !os9)
final boolean zos = (osName =~ /z\/os/)
final boolean ibmi = (osName =~ /os\/400/)

final File PLUGIN_HOME = new File(System.getenv().get("PLUGIN_HOME"))

File AGENT_HOME;
if (System.getenv().get("AGENT_HOME")){
    AGENT_HOME = new File(System.getenv().get("AGENT_HOME"))
}

final Processes processes = new Processes()

def String getArch() {
    String result
    String arch = System.getProperty("os.arch").toLowerCase(Locale.US)

    if (arch.indexOf("amd64") > -1 || arch.indexOf("x64") > -1 || arch.indexOf("x86_64") > -1) {
        result = "x64"
    } else if (arch.indexOf("x86") > -1 || arch.indexOf("386") > -1 || arch.indexOf("486") > -1 ||
             arch.indexOf("586") > -1 || arch.indexOf("686") > -1 || arch.indexOf("pentium") > -1) {
        result = "x86"
    } else if (arch.indexOf("ia64") > -1 || arch.indexOf("itanium") > -1 || arch.indexOf("ia-64") > -1) {
        result = "ia64"
    } else if (arch.indexOf("ppc") > -1 || arch.indexOf("powerpc") > -1) {
        result = "ppc"
    } else if (arch.indexOf("sparc") > -1) {
        result = "sparc"
    } else if (arch.indexOf("parisc") > -1 || arch.indexOf("pa_risc") > -1 || arch.indexOf("pa-risc") > -1) {
        result = "parisc"
    } else if (arch.indexOf("alpha") > -1) {
        result = "alpha"
    } else if (arch.indexOf("mips") > -1) {
        result = "mips"
    } else if (arch.indexOf("arm") > -1) {
        result = "arm"
    } else {
        result = "unknown"
    }
	return result
}

if (windows) {
    def arch = getArch()
    def libraryPath = new File(PLUGIN_HOME, "lib/native/${arch}/WinAPI.dll")
    System.setProperty("com.urbancode.winapi.WinAPI.dllPath", libraryPath.absolutePath)
}

final def workDir = new File('.').canonicalFile
final def props = new Properties();
final def inputPropsFile = new File(args[0]);
final def inputPropsStream = null;
try {
    inputPropsStream = new FileInputStream(inputPropsFile);
    props.load(inputPropsStream);
} catch (IOException e) {
    throw new RuntimeException(e);
}

def defaultCharset = null
if (AGENT_HOME) {
    final def agentInstalledProps = new File(AGENT_HOME, "conf/agent/installed.properties")
    final def agentProps = new Properties();
    final def agentInputStream = null;
    try {
        agentInputStream = new FileInputStream(agentInstalledProps);
        agentProps.load(agentInputStream);
    } catch (IOException e) {
        throw new RuntimeException(e);
    }
    defaultCharset = agentProps['system.default.encoding']
}

final def mv_home = props['mv_home'];
final def username = props['username'];
final def password = props['password'];

def scriptBody = null

final def sciptBodyWin = "@echo off" + lineSep +
	"set MV_HOME=" + mv_home + lineSep +
	"for /f %%i in ('" + mv_home + dirSep + "tools" + dirSep + "run-web-service-encrypter.bat \"username=" + username + "\" \"password=" + password + "\"') do set mvauthtoken=%%i" + lineSep +
	"echo mvauthtoken=%mvauthtoken%"

final def sciptBodyUnix = "export MV_HOME=" + mv_home + lineSep +
	"mvauthtoken=`" + mv_home + dirSep + "tools" + dirSep + "run-web-service-encrypter.sh username=" + username + " password=" + password + " | tail -1`" + lineSep +
	"echo mvauthtoken=\$mvauthtoken"

def exitCode = -1

//
// Validation
//
if (workDir.isFile()) {
    throw new IllegalArgumentException("Working directory ${workDir} is a file!")
}

//
// Determine OS specific interpreter, script extension and script body
//
def interpreter = null;
def scriptExt = null
if (windows) {
	scriptBody = sciptBodyWin
	scriptExt = '.bat'
} else if (vms) {
	scriptBody = sciptBodyUnix //?
	scriptExt = '.com'

	// Work dir needs to be a genuine VMS style path for inclusion in DCL
	def path = workDir.path.tokenize('/\\')

	def buf = path.first()+':'    // device
	def directories = path.tail() // everything after first element
	if (directories) {
		buf += '['+directories.join('.')+']'
	}

	scriptBody = "\$ SET DEFAULT ${buf}\n\$ ${scriptBody}"
} else if (unix) {
	scriptBody = sciptBodyUnix
	interpreter = '/bin/sh'

	// Use the PASE shell on IBMi
	if(ibmi) {
		interpreter = '/QOpenSys/usr/bin/sh'
	}
} else {
	// Unknown platform and unknown interpreter, use defaultShell as interpreter if available
	interpreter = defaultShell
}

//
// Create workDir and scriptFile
//

// ensure work-dir exists
workDir.mkdirs()

// write script content (for groovy, filename must be legal java classname chars)
final def scriptFile = File.createTempFile("generateAuthToken_", scriptExt?:'.tmp')
try {
    if (defaultCharset) {
        scriptFile.setText(scriptBody, defaultCharset)
    } else {
        scriptFile.text = scriptBody // write out with platform specific line endings
    }

    //
    // Build Command Line
    //
    def commandLine = []
    if (interpreter) {
        if (windows) {
            commandLine.add('cmd')
            commandLine.add('/C')
            commandLine.add(interpreter) // tokenize?
            commandLine.add(scriptFile.absolutePath)
        } else {
            commandLine.addAll(interpreter.tokenize())
            commandLine.add(scriptFile.absolutePath)
        }
    } else {
        if (unix) {
            // fix unix execute bit
            def chmod = ['chmod', '+x', scriptFile.absolutePath].execute()
            chmod.outputStream.close()
            chmod.consumeProcessOutput(System.out, System.err)
            chmod.waitFor() // TODO check exit code?
        }
        commandLine.add(scriptFile.absolutePath)
    }

    //
    // Launch Process
    //
    def shell = new Shell(commandLine as String[]);
    shell.addEnvironmentVariable("PLUGIN_OUTPUT_PROPS", this.args[1]);
    shell.addEnvironmentVariable("PLUGIN_INPUT_PROPS", this.args[0]);
    shell.workingDirectory = workDir

    // print out command info
    println("")
	println("RapidDeploy Authentication Token Generator")
	println('===============================')
    println("Command line: ${commandLine.join(' ')}")
    println("Script content: ")
    println('-------------------------------')
    println(scriptBody)
    println('-------------------------------')
    println("Working directory: ${workDir.path}")
    println('===============================')
    println("Command output: ")
	println('-------------------------------')

    def proc = null
    if (vms) {
        proc = Runtime.runtime.exec(commandLine as String[])
    } else {
        shell.execute()
        proc = shell.process
    }

	def hook = {
		proc.destroy();
	}
	Runtime.getRuntime().addShutdownHook(hook as Thread);
	
	// Trigger early load of this class. AIX Java 5 has a bug that produces a 
	// LinkageError if this class is loaded normally. Most likely, it is an
	// issue with concurrent loading of the class in different threads.
	Class.forName("com.urbancode.commons.util.IO");
	
	// handle process IO
	proc.outputStream.close()           // close process stdin
	def outFuture = processes.redirectOutput(proc, System.out);
	def errFuture = processes.redirectError(proc, System.err);
	outFuture.await()
	errFuture.await()
	proc.waitFor()

	Runtime.getRuntime().removeShutdownHook(hook as Thread);

	// print results
	println('===============================')
	println("Command exit code: ${proc.exitValue()}")
	println("")
	
    exitCode = proc.exitValue()
} finally {
    scriptFile.delete()
}

System.exit(exitCode)
