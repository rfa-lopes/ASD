# Algoritmos e Sistemas Distribuído - Phase 2

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

#### Servers
```bash
$ sed -i 's/\r$//' scripts/start-processes-local.sh
$ ./scripts/start-processes-local.sh 3
```

#### Client
```bash
$ cd client
$ sed -i 's/\r$//' exec.sh
$ ./exec.sh 1 10 127.0.0.1:6000 50 50 
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

### Download files
```bash
$ scp -P 12034 -r asd04@cluster.di.fct.unl.pt:~/ASD/Logs-Cluster/ .
```

---
---

d!7F6Xtqf8N=#DHx