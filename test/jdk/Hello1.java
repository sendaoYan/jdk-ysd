/*
 * @test
 */
import java.io.File;
import java.nio.file.Path;
import java.util.Random;

public class Hello1 {
    public static void main(String[] args) {
        if (System.getProperty("jtreg") != null)
            throw new RuntimeException("jtreg");
        Random r = new Random();
        if( r.nextInt() % 50 == 0) {
            throw new RuntimeException("fail!");
        }
        System.out.println(r.nextInt(20000) - 10000);
        throw new RuntimeException("fail");
    }
}
