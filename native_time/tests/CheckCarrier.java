/* SPDX-License-Identifier: MIT */
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.zip.ZipFile;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import sun.misc.Unsafe;

/** Simulated VM records; actual packaged lookup/install/restore methods, no native call. */
public final class CheckCarrier {
    private static final int BASE=0xD8000000, SELF=BASE+0x1000, OWNER=BASE+0x1100;
    private static final int TABLE=BASE+0x1200, NATIVE=TABLE+4, CARRIER=NATIVE+32, SPACE=CARRIER+32;
    private static final int CARRIER_CODE=BASE+0x4000, SPACE_CODE=BASE+0x8000;
    private final NativeTime session;
    private final int[] memory = new int[0x10000 / 4];
    private static void require(boolean ok,String text) { if (!ok) throw new AssertionError(text); }
    private static Field field(String name) throws Exception {
        Field f=NativeTime.class.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private static Method method(String name,Class... args) throws Exception {
        Method m=NativeTime.class.getDeclaredMethod(name,args);m.setAccessible(true);return m;
    }
    private void put(int p,int value) { memory[(p-BASE)/4]=value; }
    private void putBytes(int p,byte[] b) {
        for(int i=0;i<b.length;i++) memory[(p-BASE+i)/4]|=(b[i]&255)<<((i&3)*8);
    }
    private CheckCarrier(byte[] carrier,byte[] space) throws Exception {
        Field uf=Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);
        session=(NativeTime)((Unsafe)uf.get(null)).allocateInstance(NativeTime.class);
        TimeResolver rom=new TimeResolver();rom.rapuMode=true;rom.time=0xF0519E3F;rom.cache=0xF08A8FB8;
        // Model executable VM pages outside the ordinary object-RAM range.
        rom.rapu.ramEnd=BASE+0x3000;
        require(!rom.ram(CARRIER_CODE,116)&&!rom.ram(SPACE_CODE,80),"separate code region");
        field("rom").set(session,rom);field("rapu").setBoolean(session,true);
        field("view").set(session,memory);field("base").setInt(session,BASE);
        field("payload").set(session,new int[512]);
        put(SELF,OWNER);put(OWNER+36,TABLE);put(TABLE,3);
        put(NATIVE+20,0xC10A);put(NATIVE+24,OWNER);
        put(CARRIER+4,CARRIER_CODE);put(CARRIER+16,carrier.length);
        put(CARRIER+20,0xA);put(CARRIER+24,OWNER);put(CARRIER+28,1<<16);
        put(SPACE+4,SPACE_CODE);put(SPACE+16,space.length);
        put(SPACE+20,0xA);put(SPACE+24,OWNER);put(SPACE+28,2<<16);
        putBytes(CARRIER_CODE,carrier);putBytes(SPACE_CODE,space);
    }
    private int lookup() throws Exception {return (Integer)method("findMethods",int.class).invoke(session,SELF);}
    private static byte[] read(ZipFile z,String name) throws Exception {
        InputStream in=z.getInputStream(z.getEntry(name));ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] b=new byte[2048];int n;while((n=in.read(b))>0)out.write(b,0,n);in.close();return out.toByteArray();
    }
    private static byte[] expectedCarrier() {
        int[] keys={0xE01D4280,0xE0114790,0xE0194798,0xE00D4800,0xE0014900,
            0xE7ED4A00,0xE7ED4B00,0xE7F11C04,0xE7E9B510,0x0000BD10};
        byte[] b=new byte[114];b[0]=0x1A;b[1]=(byte)0xAB;b[7]=111;b[11]=10;
        for(int i=0;i<10;i++) {
            for(int j=0;j<4;j++)b[12+i*8+j]=(byte)(keys[i]>>>(j*8));
            b[19+i*8]=(byte)(91+i*2);b[92+i*2]=3;b[93+i*2]=(byte)0xAC;
        }
        b[112]=4;b[113]=(byte)0xAC;return b;
    }
    private static byte[] expectedSpace() {
        byte[] b=new byte[80];b[0]=0x1A;b[1]=(byte)0xAB;b[7]=77;b[11]=8;
        for(int i=0;i<8;i++){b[13+i*8]=(byte)(i+1);b[19+i*8]=75;}
        b[76]=3;b[77]=(byte)0xAC;b[78]=4;b[79]=(byte)0xAC;return b;
    }
    private static boolean contains(byte[] data,byte[] pattern) {
        int count=0;outer:for(int i=0;i<=data.length-pattern.length;i++){
            for(int j=0;j<pattern.length;j++)if(data[i+j]!=pattern[j])continue outer;
            count++;
        }return count==1;
    }
    public static void main(String[] args) throws Exception {
        Method modelGate = method("isRapuModel", String.class);
        for (String name : new String[] {"Nokia302", "Nokia3020", "Nokia302-1", "NokiaAsha302"})
            require((Boolean)modelGate.invoke(null, name), "RM-813 model accepted: " + name);
        for (String name : new String[] {"Nokia30200", "Nokia302foo", "Nokia3020foo", "Nokia6303c", ""})
            require(!(Boolean)modelGate.invoke(null, name), "unrelated model rejected: " + name);
        byte[] carrier=expectedCarrier(),space=expectedSpace();
        ZipFile z=new ZipFile(args[0]);byte[] packaged=read(z,"NativeTime.class");z.close();
        require(contains(packaged,carrier)&&contains(packaged,space),"packaged bytecode geometry");
        for(int ret:new int[]{0xAC,0xD5,0xD6}) {
            byte[] runtime=space.clone();runtime[79]=(byte)ret;
            CheckCarrier t=new CheckCarrier(carrier,runtime);
            require(t.lookup()==0,"original/quickened method lookup");
            int[] before=t.memory.clone();
            for(int repeat=0;repeat<3;repeat++) {
                require((Integer)method("installRapu").invoke(t.session)==0,"install");
                for(int i=0;i<t.memory.length;i++) {
                    int a=BASE+4*i;
                    boolean allowed=(a>=SPACE_CODE+12&&a<SPACE_CODE+52)||a==NATIVE+4
                        ||a==CARRIER_CODE+40||a==CARRIER_CODE+48||a==CARRIER_CODE+56||a==CARRIER_CODE+64;
                    require(allowed||t.memory[i]==before[i],"installation write footprint");
                }
                require((Boolean)method("restoreBindings").invoke(t.session),"restore status");
                require(Arrays.equals(before,t.memory),"exact byte restoration");
            }
        }
        CheckCarrier t=new CheckCarrier(carrier,space);t.put(CARRIER_CODE+12,0);
        require(t.lookup()==10,"corrupt carrier rejected");
        t=new CheckCarrier(carrier,space);t.put(SPACE_CODE+76,0);
        require(t.lookup()==11,"corrupt storage rejected");
        t=new CheckCarrier(carrier,space);t.put(CARRIER+24,OWNER+4);
        require(t.lookup()==10,"foreign method rejected");
        t=new CheckCarrier(carrier,space);t.put(CARRIER+20,0x40A);
        require(t.lookup()==10,"abstract carrier rejected");
        t=new CheckCarrier(carrier,space);t.put(CARRIER+4,BASE+0x10000);
        require(t.lookup()==10,"unrepresented code rejected");
        t=new CheckCarrier(carrier,space);t.put(NATIVE+20,0xC50A);
        require(t.lookup()==6,"abstract/native flags rejected");
        t=new CheckCarrier(carrier,space);t.put(TABLE,4);
        for(int i=0;i<32;i+=4)t.put(SPACE+32+i,t.memory[(CARRIER-BASE+i)/4]);
        require(t.lookup()==10,"ambiguous carrier rejected");
        System.out.println("PASS packaged carrier/storage: separate code/object regions, VM lookup, quickened returns, "
            +"repeated installation, bounded write footprint and exact restoration.");
    }
}
