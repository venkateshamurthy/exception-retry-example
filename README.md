This project is to check/test some of the convenience classes added in [io.github.venkateshamurthy:exception-retry:1.3 as of current](https://repo1.maven.org/maven2/io/github/venkateshamurthy/exception-retry/1.3/)
It also has a small example to run a java example (a btach job kind) in a kubernetes container.
While exception-retry is the most payload this additional kubernetes example is just a qucik refresher to run a program 
in a totally completely hosted local kube container.

###  How to run the build
```mvn clean test```

### Kubernates example for ephemeral storage 

- First do a ```brew install docker minikube colima qemu```
- Ensure PATH is appropriately set in your ```$HOME/.bashrc``` __say for example as follows__
```
export HOMEBREW=/opt/homebrew
export QEMU="$HOMEBREW/Cellar/qemu/10.0.3"
export JAVA_HOME="/Library/Java/JavaVirtualMachines/openjdk-17.jdk/Contents/Home"
export PATH="$QEMU/bin:$HOMEBREW/bin:$JAVA_HOME/bin:/usr/local/bin:$PATH"
```
- Next do a ```./kube-deploy.sh```

### How to login with a  different git account
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
- ```git remote set-url origin git@github-other:venkateshamurthy/exception-retry-example.git```
