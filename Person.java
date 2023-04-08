import java.util.Stack;

import com.oocourse.elevator3.PersonRequest;
import com.oocourse.elevator3.Request;

public class Person {
    private int id;
    private int curFloor;
    private int destFloor;
    private Stack<Integer> path = null;
    private int mapVersion = -1;

    public Person(Request request) {
        try {
            PersonRequest personRequest = (PersonRequest) request;
        
            this.id = personRequest.getPersonId();
            this.curFloor = personRequest.getFromFloor() - 1;
            this.destFloor = personRequest.getToFloor() - 1;
        } catch (ClassCastException e) {
            System.err.println(e.getMessage());
        }
    }

    public int getMapVersion() {
        return mapVersion;
    }

    public int getId() {
        return id;
    }

    public int getCurFloor() {
        return curFloor;
    }

    public int getDestFloor() {
        return destFloor;
    }

    public void setCurFloor(int curFloor) {
        this.curFloor = curFloor;
        if (!path.empty()) {
            path.pop();
        }
    }

    public boolean upStair() {
        return this.curFloor < this.destFloor;
    }

    public int getTempDest() {
        return path.peek();
    }

    public void setPath(Stack<Integer> path, int mapVersion) {
        if (this.mapVersion != mapVersion) {
            this.mapVersion = mapVersion;
            this.path = path;
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + id;
        result = prime * result + curFloor;
        result = prime * result + destFloor;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Person other = (Person) obj;
        if (id != other.id) {
            return false;
        }
        if (curFloor != other.curFloor) {
            return false;
        }
        if (destFloor != other.destFloor) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return String.format("Person %d on Floor %d to Floor %d", id, curFloor, destFloor);
    }
}