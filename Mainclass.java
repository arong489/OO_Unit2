import com.oocourse.elevator3.TimableOutput;

/**
 * Mainclass
 */
public class Mainclass {

    public static void main(String[] args) {
        TimableOutput.initStartTimestamp();
        Table table = new Table();

        RequestRecord requestRecord = new RequestRecord(table);
        requestRecord.start();
        Scheduler scheduler = new Scheduler(table);
        scheduler.start();
    }
}