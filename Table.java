import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import com.oocourse.elevator3.PersonRequest;
import com.oocourse.elevator3.Request;

public class Table {
    private final ArrayList<Person> people = new ArrayList<Person>();
    private final ArrayList<Request> requests = new ArrayList<Request>();

    private ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private ReadLock addPersonLock = readWriteLock.readLock();
    private WriteLock getPersonLock = readWriteLock.writeLock();
    private ReentrantLock requestLock = new ReentrantLock();
    private Condition emptyPerson = getPersonLock.newCondition();
    private Condition emptyRequest = requestLock.newCondition();
    private byte sleepcasue = 0;

    private AtomicInteger recorded = new AtomicInteger(0);

    private AtomicBoolean nomore = new AtomicBoolean(false);

    public Table() {}

    public void add(Request request) {
        if (request instanceof PersonRequest) {
            PersonRequest personRequest = (PersonRequest) request;
            recorded.incrementAndGet();
            add(new Person(personRequest));
        } else {
            requestLock.lock();
            try {
                requests.add(request);
            } finally {
                requestLock.unlock();
            }
            awakeGetter();
        }
    }

    public void add(Person person) {
        addPersonLock.lock();
        try {
            people.add(person);
        } finally {
            addPersonLock.unlock();
        }
        awakeGetter();
    }

    public Person getPerson() {
        Person person = null;
        getPersonLock.lock();
        try {
            while (people.isEmpty() && requests.isEmpty() && !nomore.get()) {
                sleepcasue = 1;
                System.err.println("Scheduler start to sleep");
                emptyPerson.await();
            }
            sleepcasue = 0;
            if (!people.isEmpty()) {
                person = people.get(0);
                people.remove(0);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            getPersonLock.unlock();
        }
        return person;
    }

    public Request getRequest() {
        Request request = null;
        requestLock.lock();
        try {
            while (people.isEmpty() && requests.isEmpty() && !nomore.get()) {
                sleepcasue = 2;
                System.err.println("Scheduler start to sleep");
                emptyRequest.await();
            }
            sleepcasue = 0;
            if (!requests.isEmpty()) {
                request = requests.get(0);
                requests.remove(0);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            requestLock.unlock();
        }
        return request;
    }

    public void awakeGetter() {
        if (sleepcasue == 1) {
            getPersonLock.lock();
            try {
                emptyPerson.signal();
            } finally {
                getPersonLock.unlock();
            }
        } else if (sleepcasue == 2) {
            requestLock.lock();
            try {
                emptyRequest.signal();
            } finally {
                requestLock.unlock();
            }
        }
        if (sleepcasue != 0) {
            System.err.println("try to weak scheduler");
        }
    }

    public void personArrive() {
        if (recorded.decrementAndGet() == 0) {
            awakeGetter();
        }
    }

    public boolean isEmpty() {
        boolean temp = false;
        getPersonLock.lock();
        try {
            requestLock.lock();
            temp = people.isEmpty() && requests.isEmpty();
        } finally {
            getPersonLock.unlock();
            requestLock.unlock();
        }
        return temp;
    }

    public boolean isNomore() {
        return this.nomore.get() && recorded.get() == 0;
    }

    public void setNomore(boolean nomore) {
        this.nomore.set(nomore);
    }
}