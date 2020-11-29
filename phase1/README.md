# Algoritmos e Sistemas Distribuído - Phase 1

## Setup and Test (Local)

### Git Clone
```bash
$ git clone https://github.com/rfa-lopes/ASD.git
```

### Configs and Logs file
```bash
$ cat config.properties
$ cat log4j2.xml
```

### Compile
```bash
$ mvn clean package
```

### Run
```bash
$ java -jar target/asdProj.jar -conf config.properties port=5000
```
```bash
$ java -jar target/asdProj.jar -conf config.properties port=5001 contact=127.0.0.1:5000
$ java -jar target/asdProj.jar -conf config.properties port=5002 contact=127.0.0.1:5000
$ java -jar target/asdProj.jar -conf config.properties port=5003 contact=127.0.0.1:5000
$ java -jar target/asdProj.jar -conf config.properties port=5004 contact=127.0.0.1:5000
$ java -jar target/asdProj.jar -conf config.properties port=5005 contact=127.0.0.1:5000
$ java -jar target/asdProj.jar -conf config.properties port=5006 contact=127.0.0.1:5000
$ java -jar target/asdProj.jar -conf config.properties port=5007 contact=127.0.0.1:5000,127.0.0.1:5001
```
---

## Setup and Test (Cluster)

### Path
```bash
$ ls ASD/
config.properties deploy docker Dockerfile .....
```

### Send files to DI Cluster and Login with SSH
```bash
$ scp -P 12034 -r ASD asd04@cluster.di.fct.unl.pt:.
asd04@cluster.di.fct.unl.pt's password: UlTrAsEcReTpAsSwoRd
```

### SSH login
```bash
$ ssh -p 12034 asd04@cluster.di.fct.unl.pt
asd04@cluster.di.fct.unl.pt's password: UlTrAsEcReTpAsSwoRd
```

### Reserve nodes
```bash
$ oarsub -l nodes=2 -I
$ oarsub -l nodes=2,walltime=08:00:00 -I
```

### Run all tests (and wait 4 hours)
```bash
$ sed -i 's/\r$//' deploy/deploy.sh deploy/log.sh docker/start.sh docker/setupTc.sh deploy/setup.sh deploy/runTest.sh deploy/runAll.sh deploy/checkLogs.sh
$ ./deploy/runAll.sh
```

### Download files
```bash
$ scp -P 12034 -r asd04@cluster.di.fct.unl.pt:~/ASD/Logs-Cluster/ .
```

---
---

d!7F6Xtqf8N=#DHx