/* SPDX-License-Identifier: MIT */
/** Allocation-free content discovery for the full-touch VM's growable object arena.
 * Templates are instruction recipes, never firmware-address imports. All storage
 * is initialized before entering a forged-array interval. No OS state is changed.
 */
final class HeapGrowthResolver implements HeapBoosterProtocol {
    final HeapBoosterProfile profile;
    int provider, reserveSite, limitInitial, limitGrowth, heapState, currentApp;
    int pageSizeGlobal, pageGetter, mapHelper, divideHelper;
    int runtimeFree, runtimeTotal, freeShiftSite, totalShiftSite;
    int releaseHelper, releaseWrapper, areaGetter, reserveFixed, lock, unlock, releases;
    int shrinkSite, shrinkSizer, shrinkTarget, shrinkHooked, shrinks;
    int vmmMatches;
    int state, providers, runtimes, error;
    private int[] view;
    private int base;
    private static final int[] PROVIDER = HeapGrowthPatterns.PROVIDER();
    private static final int[] RUNTIME = HeapGrowthPatterns.RUNTIME();
    private static final int[] RELEASE = HeapGrowthPatterns.RELEASE();

    private static final int[] SHRINK = HeapGrowthPatterns.SHRINK();
    private static final int[] SIZE = HeapGrowthPatterns.SIZE();

    private static final int[] VMM = HeapGrowthPatterns.VMM();
    private static final int[] VMM_RESERVE = HeapGrowthPatterns.VMM_RESERVE();
    private static final int[] VMM_DISPATCH = HeapGrowthPatterns.VMM_DISPATCH();
    private static final int[] VMM_FIXED_HEAD = HeapGrowthPatterns.VMM_FIXED_HEAD();
    private static final int[] VMM_FIXED_TAIL = HeapGrowthPatterns.VMM_FIXED_TAIL();
    private static final int[] VMM_AREA = HeapGrowthPatterns.VMM_AREA();
    private static final int[] VMM_LOCK = HeapGrowthPatterns.VMM_LOCK();
    private static final int[] VMM_UNLOCK = HeapGrowthPatterns.VMM_UNLOCK();

    HeapGrowthResolver(HeapBoosterProfile p) {
        profile = p;
        if (PROVIDER.length != 128 || RUNTIME.length != 53) throw new Error();
    }
    private boolean rom(int p, int bytes) {
        // The live forged view starts at a Java object, not at ROM. Unsigned
        // indexing below intentionally wraps its address calculation.
        return profile.rom(p, bytes);
    }
    private int w(int p) { return view[(p - base) >>> 2]; }
    private int h(int p) { return (w(p & ~3) >>> ((p & 2) * 8)) & 65535; }
    private int armLiteral(int p, int reg) {
        if (!rom(p, 4)) return 0;
        int v=w(p);
        if ((v & 0xff7ff000) != (0xe51f0000 | reg << 12)) return 0;
        int a=p+8+((v & 0x800000)!=0 ? v & 4095 : -(v & 4095));
        return rom(a,4) ? w(a) : 0;
    }
    private int thumbLiteral(int p) {
        int v=h(p);
        if ((v & 0xf800)!=0x4800) return 0;
        int a=((p+4)&~3)+((v&255)<<2);
        return rom(a,4) ? w(a) : 0;
    }
    private int call(int p, boolean thumb) {
        int v=w(p);
        if (thumb ? (v&0xfe000000)!=0xfa000000 : (v&0xff000000)!=0xeb000000)
            return 0;
        int a=p+8+((v<<8)>>6)+(thumb ? (v>>>23)&2 : 0);
        return rom(a,4) ? a : 0;
    }
    private int thumbCall(int p, boolean arm) {
        int hi=h(p),lo=h(p+2);
        if ((hi&0xf800)!=0xf000 || (lo&0xf800)!=(arm?0xe800:0xf800)) return 0;
        int d=((hi&2047)<<12)|((lo&2047)<<1);
        if ((d&0x400000)!=0) d-=0x800000;
        int a=p+4+d;
        if (arm) a&=~3;
        return rom(a,4) ? a : 0;
    }
    private boolean physical(int p,int bytes) {
        return bytes>=0 && p-profile.ramStart>=0
                && p-profile.ramStart<=profile.ramEnd-profile.ramStart-bytes;
    }
    private int patchedTemplate(int i) {
        if(i==11) return 0xe3a01502; // MOV R1,#8MiB: reservation.
        if(i==46 || i==95) return 0xe5960000; // Existing page size, no shared max cache.
        if(i==48 || i==97) return 0x1a000000; // PC+8 already names MOV R1,R0.
        if(i==51 || i==100) return 0xe3a00502;
        if(i==53 || i==102) return 0xe1a00000;
        return PROVIDER[i];
    }
    private void candidate(int p,int globals) {
        if(!rom(p,512)) return;
        boolean stock=true,grown=true;
        int getter=0,mapper=0,divide=0,round=0;
        for(int i=0;i<128;i++) {
            int a=p+i*4,v=w(a),e=PROVIDER[i];
            if(i==5 || i==6) {
                if(armLiteral(a,i==5?9:6)==0) return;
            } else if((e&0xfe000000)==0xfa000000) {
                int target=call(a,true);
                if(target==0 || (getter!=0 && getter!=target)) return;
                getter=target;
            } else if((e&0xff000000)==0xeb000000) {
                int target=call(a,false);
                if(target==0) return;
                if(i==13 || i==59 || i==118) {
                    if(mapper!=0 && mapper!=target) return;
                    mapper=target;
                } else if(i==52 || i==101) {
                    if(divide!=0 && divide!=target) return;
                    divide=target;
                } else if(i==38 || i==87) {
                    if(round!=0 && round!=target) return;
                    round=target;
                } else return;
            } else {
                if(v!=e) stock=false;
                if(v!=patchedTemplate(i)) grown=false;
                if(!stock && !grown) return;
            }
        }
        int cg=armLiteral(p+20,9),pg=armLiteral(p+24,6);
        if(!physical(cg,4) || !physical(pg,8)
                || h(getter)!=0x2001 || h(getter+2)!=0x0380 || h(getter+4)!=0x4770
                || w(mapper)!=0xe92d47fc || w(mapper+4)!=0xe1a04002
                || w(mapper+8)!=0xe1a06001 || w(mapper+12)!=0xe1b09000
                || w(divide)!=0xe190c001 || w(divide+8)!=0xe071c0a0)
            return;
        providers++; provider=p; reserveSite=p+44; limitInitial=p+184; limitGrowth=p+380;
        heapState=globals;currentApp=cg;pageSizeGlobal=pg;pageGetter=getter;
        mapHelper=mapper;divideHelper=divide;state=stock?0:1;
    }
    private void initializer(int init) {
        if(!rom(init,96) || w(init+4)!=0xe92d4010 || w(init+12)!=0xe5900000
                || w(init+16)!=0xe2841000 || w(init+20)!=0xe5840004
                || w(init+24)!=0xe2840004 || w(init+32)!=0xe5840008
                || w(init+36)!=0xe5940000 || w(init+40)!=0xe3500000
                || w(init+44)!=0x1a000004 || w(init+68)!=0xe5940008
                || w(init+72)!=0xe584000c || w(init+76)!=0xe5941004
                || w(init+80)!=0xe0800001 || w(init+84)!=0xe5840014
                || w(init+88)!=0xe5840010 || armLiteral(init,0)==0) return;
        int g=armLiteral(init+8,4),wrap=call(init+28,true);
        if(!physical(g,24) || !rom(wrap,28) || h(wrap)!=0xb570 || h(wrap+2)!=0x4605
                || h(wrap+4)!=0x4602 || h(wrap+6)!=0x2001 || h(wrap+8)!=0x0380
                || h(wrap+16)!=0x6020 || h(wrap+18)!=0x6829
                || h(wrap+24)!=0x6820 || h(wrap+26)!=0xbd70) return;
        int p=thumbCall(wrap+10,true);
        if(p!=0) candidate(p,g);
    }
    private void runtime(int p) {
        if(!rom(p,106)) return;
        boolean stock=true,grown=true;
        for(int i=0;i<53;i++) {
            int v=h(p+2*i),e=RUNTIME[i];
            if((e&0xf800)==0x4800) {
                if((v&0xff00)!=(e&0xff00) || !physical(thumbLiteral(p+2*i),4)) return;
            } else {
                if(v!=e) stock=false;
                if(v!=(i==4 || i==40 ? e+0x40 : e)) grown=false;
                if(!stock && !grown) return;
            }
        }
        if((stock?0:1)!=state || thumbLiteral(p)!=currentApp
                || thumbLiteral(p+72)!=currentApp || thumbLiteral(p+56)!=thumbLiteral(p+90))
            return;
        runtimes++;runtimeFree=p;runtimeTotal=p+72;freeShiftSite=p+8;totalShiftSite=p+80;
    }
    private void release(int p) {
        if(!rom(p,14) || h(p)!=0xb508 || h(p+2)!=0x2100
                || h(p+4)!=(state==0?0x2301:0x2302) || h(p+6)!=0x466a
                || h(p+12)!=0xbd08) return;
        int helper=thumbCall(p+8,true);
        if(!rom(helper,164)) return;
        for(int i=0;i<41;i++) {
            int v=w(helper+i*4),e=RELEASE[i];
            if(i==4) {
                if(armLiteral(helper+i*4,10)!=pageSizeGlobal) return;
            } else if((e&0xfe000000)==0xfa000000) {
                int target=call(helper+i*4,true);
                if(target==0 || (i==11 && target!=pageGetter)) return;
                if(i==37 && target!=call(mapHelper+156,true)) return;
            } else if(v!=(i==27 && state==1 ? 0xe1a01b08 : e)) return;
        }
        releases++;releaseHelper=helper;releaseWrapper=p;
    }
    private void shrink(int p) {
        if(!rom(p-232,420)) return;
        boolean hooked=w(p)==0xe51ff004 && physical(w(p+4),4) && (w(p+4)&3)==0;
        if(!hooked && (w(p)!=SHRINK[0] || call(p+4,false)!=p-232)) return;
        for(int i=2;i<47;i++) {
            int a=p+4*i,v=w(a),e=SHRINK[i];
            if(i==2 || i==33 || i==41) {
                if(armLiteral(a,i==2?5:i==33?1:2)!=(i==41?heapState+24:heapState)) return;
            } else if((e&0xfe000000)==0xfa000000) {
                if(call(a,true)==0) return;
            } else if((e&0xff000000)==0xeb000000) {
                if(call(a,false)==0) return;
            } else if(v!=e) return;
        }
        int target=p-232;
        for(int i=0;i<58;i++) {
            int e=SIZE[i],a=target+4*i;
            if(i==2 || i==3 || i==22) {
                if(!physical(armLiteral(a,i==2?5:i==3?6:1),4)) return;
            } else if(w(a)!=(i>=31 && i<=36 ? target+e : e)) return;
        }
        shrinks++;shrinkSite=p;shrinkSizer=target;shrinkHooked=hooked?1:0;
        shrinkTarget=hooked?w(p+4):0;
    }
    private boolean thumbRecipe(int p,int[] recipe) {
        if(!rom(p,recipe.length*2)) return false;
        for(int i=0;i<recipe.length;i++) {
            int e=recipe[i],a=p+2*i,v=h(a);
            if((e&0xf800)==0xf000 && i+1<recipe.length
                    && ((recipe[i+1]&0xf800)==0xf800 || (recipe[i+1]&0xf800)==0xe800)) {
                if(thumbCall(a,(recipe[i+1]&0xf800)==0xe800)==0) return false;
                i++;
            } else if((e&0xf800)==0x4800) {
                if((e&0xff00)!=(v&0xff00) || !rom(((a+4)&~3)+((v&255)<<2),4)) return false;
            } else if((e&0xf800)==0xa000) {
                if((e&0xff00)!=(v&0xff00) || !rom(((a+4)&~3)+((v&255)<<2),1)) return false;
            } else if(v!=e) return false;
        }
        return true;
    }
    private void vmm(int p) {
        if(!thumbRecipe(p,VMM)) return;
        int lk=thumbCall(p+26,false),ul=thumbCall(p+96,false),worker=thumbCall(p+64,false);
        if(!thumbRecipe(lk,VMM_LOCK) || !thumbRecipe(ul,VMM_UNLOCK)
                || !thumbRecipe(worker,VMM_RESERVE)) return;
        int area=thumbCall(worker+54,false),dispatch=thumbCall(worker+94,false);
        if(!rom(area,12) || h(area)!=0xb510 || h(area+10)!=0xbd10
                || !thumbRecipe(thumbCall(area+6,false),VMM_AREA)
                || !thumbRecipe(dispatch,VMM_DISPATCH)) return;
        int task=thumbCall(area+2,false);
        if(!rom(task,8) || h(task)!=0xb510 || h(task+6)!=0xbd10) return;
        int taskCore=thumbCall(task+2,false);
        if(taskCore==0 || thumbCall(lk+4,false)!=taskCore || thumbCall(ul+6,false)!=taskCore
                || thumbCall(lk+10,false)!=thumbCall(ul+16,false)) return;
        int veneer=thumbCall(dispatch+36,true);
        if(!rom(veneer,8) || w(veneer)!=0xe51ff004 || (w(veneer+4)&1)!=1) return;
        int fixed=w(veneer+4)&~1;
        if(!thumbRecipe(fixed,VMM_FIXED_HEAD) || !thumbRecipe(fixed+266,VMM_FIXED_TAIL)) return;
        // The VMM mapper's common return must undo its exact local/argument frame.
        int branch=p+102,ins=h(branch);
        if((ins&0xf800)!=0xe000) return;
        int ret=branch+4+((ins<<21)>>20);
        if(!rom(ret,4) || h(ret)!=0xb007 || h(ret+2)!=0xbdf0) return;
        vmmMatches++;areaGetter=area|1;reserveFixed=fixed|1;lock=lk|1;unlock=ul|1;
    }
    boolean resolve(int[] memory,int first) {
        view=memory;base=first;providers=0;runtimes=0;releases=0;shrinks=0;vmmMatches=0;error=0;state=0;
        provider=runtimeFree=runtimeTotal=heapState=currentApp=pageSizeGlobal=0;
        int scanStart=profile.romStart,span=profile.romEnd-scanStart;
        if(profile.backend!=ARMV6_XGOLD
                || (first==scanStart && memory.length<span/4)) {
            error=1;return false;
        }
        int scanIndex=(scanStart-first)>>>2,initLimit=span-96,seedLimit=span-8;
        // One aligned pass locates the initializer; its calls locate the
        // provider directly. This avoids a second full provider/init search.
        // Index wrapping preserves w(address)'s 32-bit address arithmetic even
        // when a scan crosses the forged view's base. Read lookahead only for
        // matching seeds; ordinary words need one direct array access.
        for(int offset=0,index=scanIndex;offset<=initLimit;offset+=4,index=(index+1)&0x3fffffff)
            if((memory[index]&0xff7ff000)==0xe51f0000
                    && memory[(index+1)&0x3fffffff]==0xe92d4010)
                initializer(scanStart+offset);
        if(providers!=1) {error=2;return false;}
        // All independent Thumb/ARM seeds share one word pass. Both Thumb
        // alignments are checked; complete recipes and uniqueness remain intact.
        for(int offset=0,index=scanIndex;offset<=seedLimit;offset+=4,index=(index+1)&0x3fffffff) {
            int value=memory[index],hi=value>>>16;
            if((value&0xffffff00)==0xb5104800 && (memory[(index+1)&0x3fffffff]&65535)==0x2201)
                runtime(scanStart+offset);
            if((hi&0xff00)==0x4800 && memory[(index+1)&0x3fffffff]==0x2201b510)
                runtime(scanStart+offset+2);
            if(value==0x2100b508) release(scanStart+offset);
            if(hi==0xb508 && (memory[(index+1)&0x3fffffff]&65535)==0x2100)
                release(scanStart+offset+2);
            if(value==0xe92d4038 || value==0xe51ff004) shrink(scanStart+offset);
            if(value==0x20f7b5ff && (memory[(index+1)&0x3fffffff]&65535)==0x4002)
                vmm(scanStart+offset);
            if(hi==0xb5ff && memory[(index+1)&0x3fffffff]==0x400220f7)
                vmm(scanStart+offset+2);
        }
        if(runtimes!=1) {error=3;return false;}
        if(releases!=1) {error=4;return false;}
        if(shrinks!=1) {error=5;return false;}
        if(vmmMatches!=1) {error=6;return false;}
        return true;
    }
    int originalWord(int i) {
        int v=PROVIDER[i];
        if(i==5 || i==6 || (v&0xfe000000)==0xfa000000 || (v&0xff000000)==0xeb000000)
            return w(provider+4*i);
        return v;
    }
    int grownWord(int i) { return patchedTemplate(i)==PROVIDER[i] ? originalWord(i) : patchedTemplate(i); }
    private void patch(int[] r,int n,int a,int width,int desired) {
        int at=G_PATCHES+n*G_PATCH_STRIDE;
        r[at]=a;r[at+1]=width;r[at+2]=width==2?h(a):w(a);r[at+3]=desired;
    }
    void fill(int[] r,int operation,int mib) {
        r[G_PROVIDER]=provider;r[G_HEAP_STATE]=heapState;r[G_CURRENT_APP]=currentApp;
        r[G_PAGE_SIZE]=pageSizeGlobal;r[G_MAP_HELPER]=mapHelper;r[G_PAGE_GETTER]=pageGetter;
        r[G_RUNTIME_FREE]=runtimeFree;r[G_RUNTIME_TOTAL]=runtimeTotal;r[G_SHRINK]=shrinkSite;
        r[G_RELEASE_HELPER]=releaseHelper;r[G_RELEASE_WRAPPER]=releaseWrapper;
        r[G_AREA_GETTER]=areaGetter;r[G_RESERVE_FIXED]=reserveFixed;r[G_LOCK]=lock;r[G_UNLOCK]=unlock;
        boolean grown=state==1 || (operation==OP_APPLY && mib>=4);
        patch(r,0,reserveSite,4,grown?grownWord(11):originalWord(11));
        for(int block=0;block<2;block++) {
            int first=block==0?46:95;
            for(int j=0;j<4;j++) {
                int i=first+(j==0?0:j==1?2:j==2?5:7);
                patch(r,1+block*4+j,provider+i*4,4,grown?grownWord(i):originalWord(i));
            }
        }
        patch(r,9,freeShiftSite,2,grown?0x05d2:0x0592);
        patch(r,10,totalShiftSite,2,grown?0x05c0:0x0580);
        patch(r,11,releaseHelper+108,4,grown?0xe1a01b08:0xe3a01501);
        patch(r,12,releaseWrapper+4,2,grown?0x2302:0x2301);
        r[G_PATCH_COUNT]=13;
    }
}
