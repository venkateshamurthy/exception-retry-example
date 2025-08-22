This project is to check/test some of the convenience classes added in [io.github.venkateshamurthy:exception-retry:1.3 as of current](https://repo1.maven.org/maven2/io/github/venkateshamurthy/exception-retry/1.3/)
It also has a small example to run a java example (a btach job kind) in a kubernetes container.
While exception-retry is the most payload this additional kubernetes example is just a qucik refresher to run a program 
in a totally completely hosted local kube container.

### How to run the build
```mvn clean test```

### Some example unit tests to read
ErrorsTest.java has a good examples of unit tests  of how to build exceptions.
#### Examples where default exception message  is built with details driven from template
##### An example where message is autoinferred from the Errors enum and detail message markers are of the form {key}. Please refer [toCommonRTE](https://github.com/venkateshamurthy/exception-retry/blob/fce652576c8bd4f3ecbf5d52bf8dcaa653360ab0/src/main/java/io/github/venkateshamurthy/exceptional/exceptions/ExceptionCode.java#L44)
```java 
VALIDATION_ERR.toCommonRTE().setDetailedMessage(detectAndFormat("{key1} in {key2}","error", "processing"));
VALIDATION_ERR.toCommonRTE().setDetailedMessage(detectAndFormat("{key1} in {key2}", Map.of("key1","error","key2","processing")));
```
##### An example where cause, message, and details with varargs are given. Refer [toCommonRTE(message, template, args...)](https://github.com/venkateshamurthy/exception-retry/blob/fce652576c8bd4f3ecbf5d52bf8dcaa653360ab0/src/main/java/io/github/venkateshamurthy/exceptional/exceptions/ExceptionCode.java#L79)
```java 
VALIDATION_ERR.toCommonRTE(new TimeOutException(), "Some exception message", "{faultType} in {phase}","error", "processing");
VALIDATION_ERR.toCommonRTE("Some exception message", "{faultType} in {phase}","error", "processing");
```
##### An example where message is autoinferred from the Errors enum abd detail message markers are of the form {} (An SLF4J format)
```java 
VALIDATION_ERR.toCommonRTE().setDetailedMessage(detectAndFormat("{} in {}","error", "processing"));
VALIDATION_ERR.toCommonRTE("Exception message", "{} in {}","error", "processing"));
```
##### An example where message is autoinferred from the Errors enum abd detail message markers are of the form {0} {1} (An Standard JAVA format)
```java 
VALIDATION_ERR.toCommonRTE().setDetailedMessage(detectAndFormat("{0} in {1}","error", "processing"));
VALIDATION_ERR.toCommonRTE("Exception message", "{0} in {1}","error", "processing"));
```
There are other formats as well which can be  looked up from [DetailsMessageFormatters](https://github.com/venkateshamurthy/exception-retry/blob/main/src/main/java/io/github/venkateshamurthy/exceptional/exceptions/DetailsMessageFormatters.java)
### Kubernetes example for ephemeral storage requires minikube docker etc
- Install pre-requisites ```brew install docker minikube colima qemu```
- Ensure PATH is appropriately set for all the required pre-requisites in your```$HOME/.bashrc``` __say for example as follows__
```
cat >> $HOME/.bashrc << EOF
pathadd() {
    local path="$1"
    if [[ $PATH != *"$path"* ]]; then
       PATH="$path:$PATH"
    fi
}

export JAVA_HOME="/Library/Java/JavaVirtualMachines/openjdk-17.jdk/Contents/Home"
export HOMEBREW="/opt/homebrew"
export QEMU="$HOMEBREW/Cellar/qemu/10.0.3"
pathadd /usr/local/bin
pathadd $JAVA_HOME/bin
pathadd $HOMEBREW/bin
pathadd $QEMU/bin
echo $PATH
export PATH
EOF
```
- Next run ```./kube-deploy.sh```

## How to login with a  different git account
-  ```cd ~/workspace/exception-retry-example```
#### Confgure user.name and user.email with local git.login
- ```git config user.name "venkateshamurthy"```
- ```git config user.email "venkateshamurthyts@gmail.com"```
### Next, wipe out credential-csxkeychain
- ```git credential-osxkeychain erase```
### Generate another ssh-key pair and suffix it with something new or other or what ever
- the suffix _other needs to change if it is already there. so please check in ~/.ssh/config for its presence and choose something else
- ```ssh-keygen -t ed25519 -C "venkateshamurthyts@gmail.com" -f ~/.ssh/id_ed25519_other```
- ```ssh-add ~/.ssh/id_ed25519_other```
### Add the below entries to ~/.ssh/config as follows with a cat command or otherwise
```
cat >> ~/.ssh/config <<EOL
Host github-other
  HostName github.com
  User git
  IdentityFile ~/.ssh/id_ed25519_other
EOL
```
### remote set-url as follows to be able to login with venkateshamurthy
```git remote set-url origin git@github-other:venkateshamurthy/exception-retry-example.git```
