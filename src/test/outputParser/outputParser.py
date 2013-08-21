import sys
import time
fileRead = open(sys.argv[1], "r")
fileWrite = open(sys.argv[2], "w")
month = time.strftime( "%b", time.localtime( time.time() ) )
day = str(int(time.strftime( "%d", time.localtime( time.time() ) )))
strToFind = ["INFO:", "%s %s" % (month, day)]
parsingRestart = False
for line in fileRead:
    if parsingRestart:
        if "[test" in line:
            parsingRestart = False
    if not parsingRestart:
        if "SEVERE:" in line:
            parsingRestart = True
        else:
            printLine = True
            for element in strToFind:
                if element in line:
                    printLine = False
            if printLine:
                fileWrite.write(line)
fileWrite.close()
fileRead.close()
