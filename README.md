This project is to check/test some of the convenience classes added in git@github.com:venkateshamurthy/exception-retry
It also has a small example to run a java example (a btach job kind) in a kubernetes container.
While exception-retry is the most payload this additional kubernetes example is just a qucik refresher to run a program in a totally completely hosted local kube container

# First do a brew install docker minikube colima qemu
# Next do a  mvn clean package
# Next do a ./kube-deploy.sh
