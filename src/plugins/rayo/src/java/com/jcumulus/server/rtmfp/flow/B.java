package com.jcumulus.server.rtmfp.flow;

/**
 * jCumulus is a Java port of Cumulus OpenRTMP
 *
 * Copyright 2011 OpenRTMFP
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License received along this program for more
 * details (or else see http://www.gnu.org/licenses/).
 *
 *
 * This file is a part of jCumulus.
 */

import com.jcumulus.server.rtmfp.packet.*;
import com.jcumulus.server.rtmfp.publisher.E;
import com.google.common.base.Strings;
import java.util.LinkedList;
import org.apache.log4j.Logger;


public class B
{

    private static final Logger D = Logger.getLogger(B.class);
    private Packet F;
    boolean C;
    int E;
    int K;
    int J;
    LinkedList A;
    LinkedList H;
    LinkedList B;
    LinkedList I;
    LinkedList G;


    public B(AudioPacket a)
    {
        A = new LinkedList();
        H = new LinkedList();
        B = new LinkedList();
        I = new LinkedList();
        G = new LinkedList();
        F = a;
    }

    public void K()
    {
        C = true;
    }

    public void C()
    {
        C = false;
    }

    public String E()
    {
        A();
        H h = G();
        if(h == com.jcumulus.server.rtmfp.flow.H.Null)
        {
            F.D(1);
            return "";
        }
        if(h != com.jcumulus.server.rtmfp.flow.H.String)
        {
            D.error((new StringBuilder()).append("Type ").append(h).append(" is not a AMF String type").toString());
            return null;
        }
        F.D(1);
        if(E != 0)
            return J();
        if(F() == 12)
            return new String(F.F(F.C()));
        else
            return new String(F.F(F.E() & 0xffff));
    }

    public void P()
    {
        A();
        H h = G();
        if(h == com.jcumulus.server.rtmfp.flow.H.Null)
        {
            F.D(1);
            return;
        } else
        {
            D.error((new StringBuilder()).append("Type ").append(h).append(" is not a AMF Null type").toString());
            return;
        }
    }

    public Double N()
    {
        A();
        H h = G();
        if(h == com.jcumulus.server.rtmfp.flow.H.Null)
        {
            F.D(1);
            return Double.valueOf(0.0D);
        }
        if(h != com.jcumulus.server.rtmfp.flow.H.Number)
        {
            D.error((new StringBuilder()).append("Type ").append(h).append(" is not a AMF Number type").toString());
            return Double.valueOf(0.0D);
        } else
        {
            F.D(1);
            return Double.valueOf(F.B());
        }
    }

    private int H()
    {
        A();
        H h = G();
        if(h == com.jcumulus.server.rtmfp.flow.H.Null)
        {
            F.D(1);
            return 0;
        }
        if(h != com.jcumulus.server.rtmfp.flow.H.Integer && h != com.jcumulus.server.rtmfp.flow.H.Number)
        {
            D.error((new StringBuilder()).append("Type ").append(h).append(" is not a AMF Integer type").toString());
            return 0;
        }
        F.D(1);
        if(h == com.jcumulus.server.rtmfp.flow.H.Number)
            return (int)F.B();
        int i = F.J();
        if(i > 0xfffffff)
            i -= 0x20000000;
        return i;
    }

    public boolean O()
    {
        A();
        H h = G();
        if(h == com.jcumulus.server.rtmfp.flow.H.Null)
        {
            F.D(1);
            return false;
        }
        if(h != com.jcumulus.server.rtmfp.flow.H.Boolean)
        {
            D.error((new StringBuilder()).append("Type ").append(h).append(" is not a AMF Boolean type").toString());
            return false;
        }
        if(E != 0)
        {
            return F.L() != 2;
        } else
        {
            F.D(1);
            return F.L() != 0;
        }
    }

    private C D()
    {
        A();
        C c = new C();
        H h = G();
        if(h == com.jcumulus.server.rtmfp.flow.H.Null)
        {
            F.D(1);
            c.A(false);
            return c;
        }
        if(h != com.jcumulus.server.rtmfp.flow.H.Object)
        {
            D.error((new StringBuilder()).append("Type ").append(h).append(" is not a AMF Object type").toString());
            c.A(false);
            return c;
        }
        if(E == 0)
        {
            if(C)
                I.push(Integer.valueOf(F.H()));
            if(F() == 16)
            {
                F.D(1);
                c.A(J());
            } else
            {
                F.D(1);
            }
            D d = new D(E);
            A.push(d);
            if(J != 0)
                d.B = J;
            d.E = true;
            c.A(true);
            return c;
        }
        F.D(1);
        int i = F.H();
        int j = F.J();
        boolean flag = (j & 1) != 0;
        j >>= 1;
        if(!flag && j > B.size())
        {
            D.error("AMF3 reference not found");
            c.A(false);
            return c;
        }
        D d1 = new D(E);
        A.push(d1);
        if(flag)
        {
            if(C)
                B.push(Integer.valueOf(i));
        } else
        {
            d1.B = F.H();
            F.E(((Integer)B.get(j)).intValue());
            j = F.J() >> 1;
        }
        flag = (j & 1) != 0;
        j >>= 1;
        if(flag)
        {
            G.push(Integer.valueOf(i));
            c.A(J());
        } else
        if(j <= G.size())
        {
            K = F.H();
            F.E(((Integer)G.get(j)).intValue());
            j = F.J() >> 2;
            c.A(J());
        } else
        {
            D.error("AMF3 classDef reference not found");
            j = 2;
        }
        if((j & 1) != 0)
            d1.C = true;
        else
        if((j & 2) != 0)
            d1.E = true;
        j >>= 2;
        if(!d1.C)
        {
            d1.F = new LinkedList();
            for(int k = 0; k < j; k++)
            {
                String s = J();
                d1.F.add(s);
            }

        }
        A();
        c.A(true);
        return c;
    }

    private G I()
    {
        G g = new G();
        A();
        if(A.size() == 0)
        {
            D.error("AMFReader::readItem called without a AMFReader::readObject or a AMFReader::readArray before");
            g.A(com.jcumulus.server.rtmfp.flow.H.End);
            return g;
        }
        D d = (D)A.getLast();
        E = d.D;
        boolean flag = false;
        if(d.G == 17)
        {
            D.error("AMFReader::readItem on a dictionary, used AMFReader::readKey and AMFReader::readValue rather");
            g.A(com.jcumulus.server.rtmfp.flow.H.End);
            return g;
        }
        if(d.F.size() > 0)
        {
            g.A((String)d.F.getFirst());
            d.F.removeFirst();
        } else
        if(d.G == 10)
        {
            if(d.A == 0)
            {
                flag = true;
            } else
            {
                d.A--;
                g.A("");
            }
        } else
        if(!d.E)
        {
            if(d.C)
            {
                d.C = false;
                g.A(com.jcumulus.server.rtmfp.flow.H.RawObjectContent);
                return g;
            }
            flag = true;
        } else
        {
            String s = J();
            g.A(s);
            if(Strings.isNullOrEmpty(s))
            {
                if(d.G == 9)
                {
                    d.G = 10;
                    return I();
                }
                flag = true;
            } else
            if(d.G == 0);
        }
        if(flag)
        {
            if(E == 0 && d.G != 10)
            {
                byte byte0 = F.L();
                if(byte0 != 9)
                    D.error("AMF0 end marker object absent");
            }
            K = d.B;
            A();
            A.removeLast();
            g.A(com.jcumulus.server.rtmfp.flow.H.End);
            return g;
        } else
        {
            g.A(G());
            return g;
        }
    }

    private String J()
    {
        if(E == 0)
            return new String(F.A());
        int i = F.H();
        int j = F.J();
        boolean flag = (j & 1) != 0;
        j >>= 1;
        String s;
        if(flag)
        {
            s = new String(F.F(j));
            if(!Strings.isNullOrEmpty(s))
                H.push(Integer.valueOf(i));
        } else
        {
            if(j > H.size())
            {
                D.error("AMF3 string reference not found");
                return null;
            }
            K = F.H();
            F.E(((Integer)H.get(j)).intValue());
            s = new String(F.F(F.J() >> 1));
            A();
        }
        return s;
    }

    public H G()
    {
        A();
        if(E != F.H())
            if(A.size() > 0)
                E = ((D)A.getLast()).D;
            else
                E = 0;
        if(!B())
            return com.jcumulus.server.rtmfp.flow.H.End;
        byte byte0 = F();
        if(E == 0 && byte0 == 17)
        {
            F.D(1);
            E = F.H();
            if(!B())
                return com.jcumulus.server.rtmfp.flow.H.End;
            byte0 = F();
        }
        if(E != 0)
        {
            switch(byte0)
            {
            case 0: // '\0'
            case 1: // '\001'
                return com.jcumulus.server.rtmfp.flow.H.Null;

            case 2: // '\002'
            case 3: // '\003'
                return com.jcumulus.server.rtmfp.flow.H.Boolean;

            case 4: // '\004'
                return com.jcumulus.server.rtmfp.flow.H.Integer;

            case 5: // '\005'
                return com.jcumulus.server.rtmfp.flow.H.Number;

            case 6: // '\006'
                return com.jcumulus.server.rtmfp.flow.H.String;

            case 8: // '\b'
                return com.jcumulus.server.rtmfp.flow.H.Date;

            case 9: // '\t'
                return com.jcumulus.server.rtmfp.flow.H.Array;

            case 17: // '\021'
                return com.jcumulus.server.rtmfp.flow.H.Dictionary;

            case 10: // '\n'
                return com.jcumulus.server.rtmfp.flow.H.Object;

            case 12: // '\f'
                return com.jcumulus.server.rtmfp.flow.H.ByteArray;

            case 7: // '\007'
            case 11: // '\013'
            case 13: // '\r'
            case 14: // '\016'
            case 15: // '\017'
            case 16: // '\020'
            default:
                D.error((new StringBuilder()).append("Unknown AMF3 type ").append(byte0).toString());
                break;
            }
            F.D(1);
            return G();
        }
        switch(byte0)
        {
        case 5: // '\005'
        case 6: // '\006'
            return com.jcumulus.server.rtmfp.flow.H.Null;

        case 1: // '\001'
            return com.jcumulus.server.rtmfp.flow.H.Boolean;

        case 0: // '\0'
            return com.jcumulus.server.rtmfp.flow.H.Number;

        case 2: // '\002'
        case 12: // '\f'
            return com.jcumulus.server.rtmfp.flow.H.String;

        case 8: // '\b'
        case 10: // '\n'
            return com.jcumulus.server.rtmfp.flow.H.Array;

        case 11: // '\013'
            return com.jcumulus.server.rtmfp.flow.H.Date;

        case 3: // '\003'
        case 16: // '\020'
            return com.jcumulus.server.rtmfp.flow.H.Object;

        case 7: // '\007'
            F.D(1);
            short word0 = F.E();
            if(word0 > I.size())
            {
                D.error("AMF0 reference not found");
                return G();
            } else
            {
                J = F.H();
                F.E(((Integer)I.get(word0)).intValue());
                return G();
            }

        case 9: // '\t'
            D.error("AMF end object type without begin object type before");
            F.D(1);
            return G();

        case 13: // '\r'
            D.warn("Unsupported type in AMF format");
            F.D(1);
            return G();

        case 4: // '\004'
        case 14: // '\016'
        case 15: // '\017'
        default:
            D.error((new StringBuilder()).append("Unknown AMF type ").append(byte0).toString());
            F.D(1);
            return G();
        }
    }

    public com.jcumulus.server.rtmfp.flow.E L()
    {
        com.jcumulus.server.rtmfp.flow.E e = new com.jcumulus.server.rtmfp.flow.E();
        C c = D();
        if(!c.A())
            return null;
        if(!Strings.isNullOrEmpty(c.C()))
            D.warn((new StringBuilder()).append("Object seems not be a simple object because it has a ").append(c.C()).append(" type").toString());
        do
        {
            G g;
            if((g = I()).B() == com.jcumulus.server.rtmfp.flow.H.End)
                break;
            String s = g.A();
            if(com.jcumulus.server.rtmfp.flow.H.Null == g.B())
            {
                P();
                e.G(s);
                continue;
            }
            if(com.jcumulus.server.rtmfp.flow.H.Boolean == g.B())
            {
                e.A(s, O());
                continue;
            }
            if(com.jcumulus.server.rtmfp.flow.H.Integer == g.B())
            {
                e.A(s, H());
                continue;
            }
            if(com.jcumulus.server.rtmfp.flow.H.String == g.B())
            {
                String s1 = E();
                e.B(s, s1);
                continue;
            }
            if(com.jcumulus.server.rtmfp.flow.H.Number == g.B())
            {
                e.A(s, N().doubleValue());
                continue;
            }
            if(com.jcumulus.server.rtmfp.flow.H.Date == g.B())
                break;
            D.error((new StringBuilder()).append("AMF ").append(g.B()).append(" type unsupported in an AMFDataObj conversion").toString());
            F.D(1);
        } while(true);
        return e;
    }

    private byte F()
    {
        return F.G()[0];
    }

    public boolean B()
    {
        A();
        return F.I() > 0;
    }

    private void A()
    {
        if(K > 0)
        {
            F.E(K);
            K = 0;
        }
    }

    public Packet M()
    {
        return F;
    }


}
