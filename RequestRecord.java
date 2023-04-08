import java.io.IOException;

import com.oocourse.elevator3.ElevatorInput;
import com.oocourse.elevator3.Request;

public class RequestRecord extends Thread {
    private final Table table;

    public RequestRecord(Table table) {
        this.table = table;
    }

    @Override
    public void run() {
        ElevatorInput elevatorInput = new ElevatorInput();
        while (true) {
            Request request = elevatorInput.nextRequest();
            if (request == null) {
                table.setNomore(true);
                break;
            } else {
                table.add(request);
            }
        }
        
        try {
            elevatorInput.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        table.awakeGetter();
    }
}
