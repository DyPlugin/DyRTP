// Java port of render_chart.py for hosts without Python/Pillow: `java scripts/comparison/RenderChart.java`.
// Draws the same 2000x1680 PNG and editable SVG from the five final rows in docs/betterrtp-comparison.md.
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;

public class RenderChart {
    static final int W=2000,H=1680;
    static final Color BG=new Color(0xf4f2ed),INK=new Color(0x18191b),MUTED=new Color(0x686862),GREEN=new Color(0x248b46),GRAY=new Color(0xaaa8a0),GRID=new Color(0xe2dfd7),RULE=new Color(0xd7d3cb);
    static Graphics2D g;static final List<String> svg=new ArrayList<>();static Font regular,bold;
    static String hex(Color c){return String.format("#%06x",c.getRGB()&0xffffff);}
    static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    static void text(int x,int y,String value,int size,Color color,boolean isBold,boolean right){
        Font font=(isBold?bold:regular).deriveFont((float)size);g.setFont(font);FontMetrics metrics=g.getFontMetrics();int width=metrics.stringWidth(value);
        int actual=right?x-width:x;if(actual<0||actual+width>W)throw new IllegalStateException("Text outside canvas: "+value);
        g.setColor(color);g.drawString(value,actual,y+metrics.getAscent());
        svg.add("<text x=\""+x+"\" y=\""+y+"\" fill=\""+hex(color)+"\" font-family=\"Arial, sans-serif\" font-size=\""+size+"\" font-weight=\""+(isBold?700:400)+"\" dominant-baseline=\"text-before-edge\" text-anchor=\""+(right?"end":"start")+"\">"+escape(value)+"</text>");
    }
    static void rect(double x,int y,double width,int height,Color color){
        g.setColor(color);g.fillRect((int)Math.round(x),y,(int)Math.round(width)+1,height+1);
        svg.add(String.format(Locale.ROOT,"<rect x=\"%s\" y=\"%d\" width=\"%.3f\" height=\"%d\" fill=\"%s\"/>",x==(int)x?Integer.toString((int)x):Double.toString(x),y,width,height,hex(color)));
    }
    public static void main(String[] args)throws Exception{
        Path project=Path.of(args.length>0?args[0]:".").toAbsolutePath();
        String report=Files.readString(project.resolve("docs/betterrtp-comparison.md"),StandardCharsets.UTF_8);
        Matcher m=Pattern.compile("^\\| (Overworld .*?|Nether .*?|End .*?) \\| (\\d+) \\| ([\\d.]+) / ([\\d.]+) \\| ([\\d.]+) / ([\\d.]+) \\|$",Pattern.MULTILINE).matcher(report.replace("\r\n","\n"));
        List<String[]> rows=new ArrayList<>();while(m.find())rows.add(new String[]{m.group(1),m.group(2),m.group(3),m.group(4),m.group(5),m.group(6)});
        if(rows.size()!=5)throw new IllegalStateException("Expected exactly five final-artifact scenarios, found "+rows.size());
        if(rows.stream().mapToInt(r->Integer.parseInt(r[1])).sum()!=70)throw new IllegalStateException("Expected 70 requests");
        regular=Font.createFont(Font.TRUETYPE_FONT,new File("C:/Windows/Fonts/arial.ttf"));bold=Font.createFont(Font.TRUETYPE_FONT,new File("C:/Windows/Fonts/arialbd.ttf"));
        BufferedImage image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setColor(BG);g.fillRect(0,0,W,H);
        svg.add("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\""+W+"\" height=\""+H+"\" viewBox=\"0 0 "+W+" "+H+"\">");
        svg.add("<title>DyRTP versus BetterRTP: median command-to-teleport time</title>");
        svg.add("<desc>Five matched-setting samples on Paper 26.1.2. 70 successful requests per plugin. Lower is faster. One client; protection and queue disabled; not CPU or RAM usage.</desc>");
        svg.add("<rect width=\""+W+"\" height=\""+H+"\" fill=\""+hex(BG)+"\"/>");
        text(150,80,"DyRTP",68,INK,true,false);
        text(1850,104,"2 PLUGINS · 70 RTPs EACH",26,MUTED,false,true);
        text(150,254,"Same settings. Measured RTP.",68,INK,true,false);
        text(150,356,"Lower median time in these samples.",64,GREEN,true,false);
        text(150,454,"Command-to-teleport time. Lower is faster.",34,MUTED,false,false);
        rect(150,523,26,26,GREEN);text(190,518,"DyRTP 1.1.0",30,INK,true,false);
        rect(515,523,26,26,GRAY);text(555,518,"BetterRTP 3.6.13",30,INK,false,false);
        text(1850,520,"MEDIAN · ms",28,MUTED,false,true);
        int left=150;double plot=1450,maximum=200.0;
        for(int index=0;index<rows.size();index++){
            String[] row=rows.get(index);int y=592+index*144;
            String label=row[0].replace(" first pass"," · first pass").replace(" second pass"," · second pass").replace("Overworld ","Overworld · ").replace("Nether ","Nether · ").replace("End ","End · ");
            text(left,y,label,32,INK,true,false);text(1850,y+3,row[1]+" RTPs / plugin",26,MUTED,false,true);
            for(Object[] bar:new Object[][]{{49,Double.parseDouble(row[2]),GREEN},{87,Double.parseDouble(row[4]),GRAY}}){
                int offset=(Integer)bar[0];double value=(Double)bar[1];Color color=(Color)bar[2];if(value<0||value>maximum)throw new IllegalStateException("Value outside scale: "+value);
                rect(left,y+offset,plot,24,GRID);rect(left,y+offset,plot*value/maximum,24,color);
                text(1850,y+offset-8,String.format(Locale.ROOT,"%.2f ms",value),34,color==GREEN?GREEN:INK,true,true);
            }
        }
        for(int value:new int[]{0,50,100,150,200}){int x=(int)Math.round(left+plot*value/maximum);text(x,1312,Integer.toString(value),24,MUTED,false,value==200);}
        rect(150,1370,1700,2,RULE);
        text(150,1401,"70 successful RTPs each. Basic check failures: DyRTP 0; BetterRTP 0.",29,INK,true,false);
        text(150,1454,"Paper 26.1.2 · Java 25 · one client · restored world snapshot · queue and protection disabled.",25,MUTED,false,false);
        text(150,1497,"Median samples: 10–20 requests per scenario. Random destinations; not CPU/RAM usage.",25,MUTED,false,false);
        text(150,1540,"Flat Overworld; results are not a general performance or safety guarantee. Method and p95 in the report.",25,MUTED,false,false);
        text(150,1620,"modrinth.com/plugin/dyrtp",28,INK,true,false);
        text(1850,1624,"24.10.2026",24,MUTED,false,true);
        svg.add("</svg>");g.dispose();
        Path output=project.resolve("docs/images");Files.createDirectories(output);
        ImageIO.write(image,"png",output.resolve("DyRTP-vs-BetterRTP-en.png").toFile());
        Files.writeString(output.resolve("DyRTP-vs-BetterRTP-en.svg"),String.join("\n",svg),StandardCharsets.UTF_8);
        System.out.println("Rendered 2000x1680 PNG and SVG from five documented final measurement rows.");
    }
}
