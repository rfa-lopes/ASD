f = open("latency", "w")

for x in range(0,100):
    for y in range(0,100):
        if(x == y):
            f.write("0")
        else:
            f.write("100")
        if(y != 99):
            f.write("      ")
    f.write('\n')

f.close()
