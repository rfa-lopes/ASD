import os
import matplotlib.pyplot as plt
import numpy as np
import datetime
#Very Hard Coded script to produce statistics on Logs received from nodes at the cluster.
#Just place the logs in the logs folder. The folder should contain 2 directories
#both have logs from the same protocols stack, but in each directory 2 variables change
#The payload size and bitrate
#This script produces three images, first is the graphic bar of SentMessages
#The other is the Failed Messages and finnaly the reliability
#More statistics could be introduced if wished.


rootdir = 'logs'
label1 ="Payload=10B and rate=1s"
label3 ="Payload=10B and rate=3s"
label4 ="Payload=1000B and rate=1s"
label2 ="Payload=1000B and rate=3s"
label5 ="Size in bytes"
title = "Plumtree + Hyparview"
SentMessages=[]
FailedMessages=[]
lastMetrics =""
listval =[]
failedvals =[]
nfiles = 0
size1 = 0
size2 = 0
testalllat = []


for subdir, dirs, files in os.walk(rootdir):
    # iterates each log direcory for analising payldsize and messageRate
    for dir2 in dirs:
        for dir in os.listdir("C:/Users/mikep/PycharmProjects/logcomputations/logs/"+dir2):
            listval = []
            failedvals = []
            mtimes = dict()
            nfiles=0
            for file in os.listdir("C:/Users/mikep/PycharmProjects/logcomputations/logs/"+dir2+"/"+dir):
                nfiles = nfiles + 1
                qbfile = open("C:/Users/mikep/PycharmProjects/logcomputations/logs/"+dir2+"/"+dir+"/"+file, "r")
                sentCount = 0
                receivedCount = 0
                lastMetrics=""
                for line in qbfile:
                    if "m Sent" in line:
                        sentCount = sentCount + 1

                    if "m Received GossipMessage" in line:
                        receivedCount = receivedCount +1


                    if "ProtocolMetrics" in line:
                        lastMetrics = line

                    if "m Sending" in line:
                        msgid = line[line.index("m Sending: "):len(line)-1]
                        msgid=msgid.split(':')[1]
                        timestring = line[7:19]
                        splited = timestring.split(':')
                        micro = splited[2].split(',')
                        now = datetime.datetime.now()
                        time = now.replace(hour=int(splited[0]), minute=int(splited[1]), second=int(micro[0]), microsecond=int(micro[1]))
                        if msgid in mtimes:
                            if time < mtimes[msgid][0]:
                                mtimes[msgid][0] = time

                            if time > mtimes[msgid][1]:
                                mtimes[msgid][1] = time

                        else:
                            vals = []
                            vals.append(time)
                            vals.append(time)
                            mtimes[msgid] = vals

                    if "BroadcastApp" in line and "m Received" in line:
                        msgid = line[line.index("m Received "):line.index(" from")]
                        msgid2=msgid.split(' ')[2] +msgid.split(' ')[3] + msgid.split(' ')[4] +msgid.split(' ')[5]
                        timestring = line[7:19]
                        splited = timestring.split(':')
                        micro = splited[2].split(',')
                        now = datetime.datetime.now()
                        time = now.replace(hour=int(splited[0]), minute=int(splited[1]), second=int(micro[0]), microsecond=int(micro[1]))
                        if msgid2 in mtimes:
                            if time < mtimes[msgid2][0]:
                                mtimes[msgid2][0] = time

                            if time > mtimes[msgid2][1]:
                                mtimes[msgid2][1] = time

                        else:
                            vals = []
                            vals.append(time)
                            vals.append(time)
                            mtimes[msgid2] = vals



                listval.append(sentCount)
                failedvals.append(lastMetrics.split(",")[2].split("=")[1])

            SentMessages.append(listval)
            FailedMessages.append(failedvals)
            #calc avrg latency
            totallatency = 0
            for key in mtimes:
                duration = mtimes[key][1] - mtimes[key][0]
                totallatency = totallatency + float(duration.total_seconds())

            totallatency = totallatency/nfiles
            testalllat.append(totallatency)

    break


y = []
b =[]
for j in range(0, nfiles):
    y.append(j)
    b.append(j)


ind = np.arange(nfiles)

# Figure size
# plt.figure(figsize=(10,5))

# Width of a bar
width = 0.4

totalsizes = []
for j in SentMessages[0]:
    totalsizes.append(j*10)



totalsizes2 = []
for j in SentMessages[1]:
    totalsizes2.append(j*10)

totalsizes3 = []
for j in SentMessages[2]:
    totalsizes3.append(j*10000)

totalsizes4 = []
for j in SentMessages[3]:
    totalsizes4.append(j*10000)



fig = plt.figure()
fig.set_figheight(10)
fig.set_figwidth(10)

# Plotting
plt.bar(ind,SentMessages[0] , width, label=label1)
# plt.bar(ind+ width , totalsizes, width, label="wat")

plt.bar(ind+ width , SentMessages[1], width, label=label3)
# plt.bar(ind+ width , totalsizes2, width, label=label5)

plt.bar(ind+ width , SentMessages[2], width, label=label4)
# plt.bar(ind+ width , totalsizes3, width, label=label5)

plt.bar(ind+ width , SentMessages[3], width, label=label2)
# plt.bar(ind+ width , totalsizes4, width, label=label5)




plt.xlabel('Nodes')
plt.ylabel('Messages Sent')
plt.title(title)

# xticks()
# First argument - A list of positions at which ticks should be placed
# Second argument -  A list of labels to place at the given locations
# for j in range(0, nfiles):
#     plt.xticks(ind + width / 2, ('Xtick1', 'Xtick3', 'Xtick3'))

# Finding the best position for legends and putting it
plt.legend(loc='best')
plt.savefig(title+"Sent", dpi=300)

plt.clf()

totalfail = []
totalfail1 = []
totalfail2 = []
totalfail3 = []

for j in FailedMessages[0]:
    totalfail.append(j*10)

for j in FailedMessages[1]:
    totalfail1.append(j*10)

for j in FailedMessages[2]:
    totalfail2.append(j*10000)

for j in FailedMessages[3]:
    totalfail3.append(j*10000)

plt.bar(ind,FailedMessages[0] , width, label=label1)
# plt.bar(ind+ width , totalfail, width, label="wat1")

plt.bar(ind+ width , FailedMessages[1], width, label=label3)
# plt.bar(ind+ width , totalfail1, width, label="wat3")

plt.bar(ind+ width , FailedMessages[2], width, label=label4)
# plt.bar(ind+ width , totalfail2, width, label="watwow")

plt.bar(ind+ width , FailedMessages[3], width, label=label2)
# plt.bar(ind+ width , totalfail3, width, label="nani")


plt.xlabel('Nodes')
plt.ylabel('Failed Messages')
plt.title(title)

plt.legend(loc='best')
plt.savefig(title+"FailedMessages", dpi=300)

plt.clf()

relial =[]
relial1 =[]
relial2 =[]
relial3 =[]

test = FailedMessages[0]
total = SentMessages[0]
for val in range(0, nfiles):
    try:
        relial.append((1-(int(test[val])/total[val])))
    except ZeroDivisionError :
        relial.append(0)


test = FailedMessages[1]
total = SentMessages[1]
for val in range(0, nfiles):
    try:
        relial1.append((1-(int(test[val])/total[val])))
    except ZeroDivisionError:
        relial1.append(0)

test = FailedMessages[2]
total = SentMessages[2]
for val in range(0, nfiles):
    try:
        relial2.append((1-(int(test[val])/total[val])))
    except ZeroDivisionError:
        relial2.append(0)

test = FailedMessages[3]
total = SentMessages[3]
for val in range(0, nfiles):
    try:
        relial3.append((1-(int(test[val])/total[val])))
    except ZeroDivisionError:
        relial3.append(0)


plt.bar(ind, relial , width, label=label1)

plt.bar(ind+ width , relial1, width, label=label3)

plt.bar(ind+ width, relial2,  width, label=label4)

plt.bar(ind+ width , relial3, width, label=label2)

plt.xlabel('Nodes')
plt.ylabel('Avrg reliability')
plt.title(title)

plt.legend(loc='best')
plt.savefig(title+"Avrg_Reliability", dpi=300)

f = open(title+".txt", "x")
f.write("Avrg Latency by test s\n")

for val in testalllat:
    f.write("Test 1 -> "+ str(round(val, 2)) +'s\n')


f.close()
