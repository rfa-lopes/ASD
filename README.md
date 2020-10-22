# Algoritmos e Sistemas Distribuído

## Setup and Test

### Git Clone
```bash
git clone https://github.com/rfa-lopes/ASD.git
```

### Compile
```bash
mvn package
```

### Run
```bash
java -jar target/asdProj.jar -conf config.properties port=5000
```
```bash
java -jar target/asdProj.jar -conf config.properties port=5000 contact=127.0.0.1:5000
```

---

## Informação adicional

### Comandos Git
```bash
git pull origin master
git add .
git commit -m "Initial commit"
git push
git rm -r --cached Path/to/directories
```

### Autores
* Rodrigo Lopes - rfa.lopes@campus.fct.unl.pt - 50435
* André Candeias - mb.candeias@campus.fct.unl.pt - 50647
* Salvador Rosa Mendes - sr.mendes@campus.fct.unl.pt - 50503
