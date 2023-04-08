import java.util.ArrayList;
import java.util.Stack;
import java.util.concurrent.Semaphore;

import com.oocourse.elevator3.ElevatorRequest;
import com.oocourse.elevator3.MaintainRequest;
import com.oocourse.elevator3.Request;

public class Scheduler extends Thread {
    private final Table table;
    private final ArrayList<Elevator> elevators = new ArrayList<>();
    private final ArrayList<Semaphore> onlyLetIn = new ArrayList<>();
    private final ArrayList<Semaphore> onServe = new ArrayList<>();
    private int [] access = new int[11];
    private int mapVersion = 0;
    private boolean refreshmap = true;

    private boolean end = false;

    public Scheduler(Table table) {
        this.table = table;
        for (int i = 0; i < 11; i++) {
            onServe.add(new Semaphore(2, true));
            onlyLetIn.add(new Semaphore(4, true));
        }
        for (int i = 1; i < 7; i++) {
            elevators.add(new Elevator(i, this, onlyLetIn, onServe, table));
        }
    }

    @Override
    public void run() {
        elevators.forEach(elevator -> {
            elevator.start();
        });
        while (!table.isEmpty() || !table.isNomore()) {
            Request request = table.getRequest();
            while (request != null) {
                handle(request);
                request = table.getRequest();
            }
            Person person = table.getPerson();
            handle(person);
        }
        System.err.println("!!!scheduler died");
        end = true;
        elevators.forEach(elevator -> {
            elevator.awake();
        });
    }

    public boolean isEnd() {
        return end;
    }

    private void handle(Request request) {
        if (request == null) {
            return;
        }
        refreshmap = true;
        if (request instanceof ElevatorRequest) {
            ElevatorRequest elevatorRequest = (ElevatorRequest) request;
            Elevator elevator = new Elevator(elevatorRequest, this, onlyLetIn, onServe, table);
            elevators.add(elevator);
            System.err.println("Add elevator " + elevatorRequest.getElevatorId());
            elevator.start();
        } else {
            MaintainRequest maintainRequest = (MaintainRequest) request;
            int i = 0;
            for (; i < elevators.size(); i++) {
                if (elevators.get(i).getElevatorId() == maintainRequest.getElevatorId()) {
                    elevators.get(i).setIfmaintian(true);
                    break;
                }
            }
            elevators.remove(i);
        }
    }

    private void handle(Person person) {
        if (person == null) {
            return;
        }
        updateMap();
        checkpath(person);
        int pathVector = (1 << person.getTempDest()) | (1 << person.getCurFloor());
        int minPlace = 0;
        int minCost = 0x7fffffff;
        int tempCost;
        Elevator elevator;
        for (int i = 0; i < elevators.size(); i++) {
            elevator = elevators.get(i);
            if ((elevator.getAvailable() & pathVector) == pathVector) {
                elevator = elevators.get(i);
                int distance = elevator.getCurFloor() - person.getCurFloor();
                tempCost = (elevator.getCurNum() / elevator.getMaxMem()) * elevator.getMoveCost()
                    + (distance < 0 ? -distance : distance);
                if (tempCost < minCost) {
                    minCost = tempCost;
                    minPlace = i;
                }
            }
        }
        elevators.get(minPlace).addTask(person);
    }

    private void checkpath(Person person) {
        if (person == null || person.getMapVersion() == mapVersion) {
            return;
        }
        // dijkstra
        //initial
        int[] path = new int[11];
        int[] cost = new int[11];
        int nowArrived = person.getCurFloor();
        int nextArrived = access[nowArrived];
        for (int i = 0; i < 11; i++) {
            if ((nextArrived & (1 << i)) != 0) {
                cost[i] = 1;
                path[i] = nowArrived;
            } else {
                cost[i] = 0x7fffffff;
                path[i] = -1;
            }
        }
        nowArrived = 1 << nowArrived;
        int minCost;
        int minPlace = 0;
        while ((nowArrived & (1 << person.getDestFloor())) == 0) {
            minCost = 0x7fffffff;
            for (int i = 0; i < 11; i++) {
                if ((nowArrived & (1 << i)) == 0 && minCost > cost[i]) {
                    minCost = cost[i];
                    minPlace = i;
                }
            }
            nowArrived |= (1 << minPlace);
            for (int i = 0; i < 11; i++) {
                if ((nowArrived & (1 << i)) == 0 && (access[minPlace] & (1 << i)) != 0) {
                    if (cost[i] > cost[minPlace] + 1) {
                        cost[i] = cost[minPlace] + 1;
                        path[i] = minPlace;
                    }
                }
            }
        }
        // Queue<Integer> pathes = new LinkedList<>();
        Stack<Integer> pathes = new Stack<>();
        int depareture = person.getCurFloor();
        for (int i = person.getDestFloor(); i != depareture; i = path[i]) {
            pathes.add(i);
        }
        person.setPath(pathes, mapVersion);
    }

    private void updateMap() {
        if (!refreshmap) {
            return;
        }
        refreshmap = false;
        mapVersion++;
        for (int i = 0; i < 11; i++) {
            access[i] = 0;
        }
        for (Elevator elevator : elevators) {
            int available = elevator.getAvailable();
            for (int i = 0; i < 11; i++) {
                if ((available & (1 << i)) != 0) {
                    access[i] |= available ^ (1 << i);
                }
            }
        }
    }
}