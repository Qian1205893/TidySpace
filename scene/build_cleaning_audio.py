"""Small deterministic, loop-safe tool foley clips, 22.05 kHz mono PCM."""
import math
import random
import struct
import wave
from pathlib import Path

OUT=Path(__file__).resolve().parents[1]/"app/src/main/assets/sounds"
for name,duration in (("clean_sweep",.28),("clean_scrub",.32),("clean_motor",1.0),("clean_suction",.23)):
    rng=random.Random(name)
    samples=[]
    low=0.0
    for n in range(int(duration*22050)):
        t=n/22050
        noise=rng.uniform(-1,1)
        low=low*.83+noise*.17
        if name=="clean_motor":
            value=.09*math.sin(t*math.tau*110)+.03*math.sin(t*math.tau*220)+.035*low
            envelope=min(1,t/.012,(duration-t)/.012)
        else:
            envelope=math.sin(math.pi*t/duration)**1.5
            value=(noise*.12+low*.19)*(1+.25*math.sin(t*math.tau*17))
            if name=="clean_scrub": value=low*.45+noise*.04
            if name=="clean_suction": value=low*.23+math.sin(t*math.tau*(340+t*300))*.035
        samples.append(int(max(-1,min(1,value*envelope))*32767))
    with wave.open(str(OUT/(name+".wav")),"wb") as w:
        w.setnchannels(1);w.setsampwidth(2);w.setframerate(22050)
        w.writeframes(struct.pack("<"+"h"*len(samples),*samples))
