import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.oocourse.elevator3.ElevatorRequest;
import com.oocourse.elevator3.TimableOutput;

public class Elevator extends Thread {
    private int available = 0x7ff;
    private int moveCost = 400;
    private int openCost = 200;
    private int closeCost = 200;
    private int maxMem = 6;
    private int id;
    // status figures
    private long lasttime = 0;//Quantization the elevator time
    
    private ReentrantLock stautsLock = new ReentrantLock();
    private Condition notask = stautsLock.newCondition();
    private boolean ifmaintain = false;//赋值or读取是原子操作
    private AtomicInteger curFloor = new AtomicInteger(0);//int自加减非原子操作
    private int curDire = 0;//赋值是原子操作
    private AtomicInteger curNum = new AtomicInteger(0);//自加减不是原子操作
    private final ArrayList<Semaphore> onlyLetIn;
    private final ArrayList<Semaphore> onServe;

    private final Scheduler scheduler;

    private final ArrayList<Person> onElevator = new ArrayList<>();
    private int destination;
    private final ArrayList<Person> waitUp = new ArrayList<>();
    private int bitUp;
    private final ArrayList<Person> waitDown = new ArrayList<>();
    private int bitDown;
    private Table table;

    public Elevator(int id, Scheduler scheduler, ArrayList<Semaphore> onlyLetIn,
        ArrayList<Semaphore> onServe, Table table) {
        this.id = id;
        this.scheduler = scheduler;
        this.onlyLetIn = onlyLetIn;
        this.onServe = onServe;
        this.table = table;
    }

    public Elevator(ElevatorRequest elevatorRequest, Scheduler scheduler,
        ArrayList<Semaphore> onlyLetIn, ArrayList<Semaphore> onServe,
        Table table) {
        this.available = elevatorRequest.getAccess();
        this.moveCost = (int)(elevatorRequest.getSpeed() * 1000);
        this.maxMem = elevatorRequest.getCapacity();
        this.id = elevatorRequest.getElevatorId();
        this.curFloor.set(elevatorRequest.getFloor() - 1);
        this.scheduler = scheduler;
        this.lasttime = System.currentTimeMillis();
        this.onServe = onServe;
        this.onlyLetIn = onlyLetIn;
        this.table = table;
    }

    @Override
    public void run() {
        boolean noTask = (onElevator.size() == 0 && waitUp.size() == 0 && waitDown.size() == 0);
        while ((!scheduler.isEnd() || !noTask) || ifmaintain) {
            while (noTask && !scheduler.isEnd() && !ifmaintain) {
                await();
                noTask = (onElevator.size() == 0 && waitUp.size() == 0 && waitDown.size() == 0);
            }
            if (scheduler.isEnd() && noTask && !ifmaintain) {
                break;
            }
            if (ifmaintain) {
                maintain();
                break;
            }
            if (hasOut()) {
                open(false);
                letOut();
                if (hasIn()) {
                    letIn();
                }
                close(false);
            } else if (hasIn()) {
                open(true);
                letIn();
                close(true);
            }
            if (ifmaintain) {
                maintain();
                break;
            }
            setDirection();
            move();
            noTask = (onElevator.size() == 0 && waitUp.size() == 0 && waitDown.size() == 0);
        }
        ifmaintain = true;
    }

    public void await() {
        try {
            stautsLock.lock();  
            notask.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            stautsLock.unlock();
        }
    }

    public void awake() {
        try {
            stautsLock.lock();
            notask.signal();
        } finally {
            stautsLock.unlock();
        }
    }

    public void addTask(Person person) {
        stautsLock.lock();
        try {
            if (person.upStair()) {
                waitUp.add(person);
                bitUp |= 1 << person.getCurFloor();
            } else {
                waitDown.add(person);
                bitDown |= 1 << person.getCurFloor();
            }
        } finally {
            notask.signal();
            stautsLock.unlock();
        }
    }

    public void setIfmaintian(boolean ifmaintain) {
        if (this.ifmaintain) {
            TimableOutput.println(String.format("MAINTAIN_ABLE-%d", id));
            return;
        }
        this.ifmaintain = ifmaintain;
        awake();
    }

    public int getCurFloor() {
        return curFloor.get();
    }

    public int getCurNum() {
        return curNum.get() + waitUp.size() + waitDown.size();
    }

    public int getElevatorId() {
        return id;
    }

    public int getAvailable() {
        return available;
    }

    public int getMoveCost() {
        return moveCost;
    }

    public int getMaxMem() {
        return maxMem;
    }

    private void setDirection() {
        int lowcall;
        if (curNum.get() == maxMem) {
            lowcall = destination;
        } else {
            lowcall = destination | bitDown | bitUp;//原子操作?
        }
        if (lowcall == (1 << curFloor.get()) || lowcall == 0) {
            curDire = 0;
            return;
        }
        int highcall = lowcall >> (curFloor.get() + 1);
        lowcall &= ((1 << curFloor.get()) - 1);
        if (curDire == 0) {
            if (curFloor.get() > 5) {
                curDire = highcall != 0 ? 1 : 2;
            } else {
                curDire = lowcall != 0 ? 2 : 1;
            }
        } else if (curDire == 1 && highcall == 0) {
            curDire = 2;
        } else if (curDire == 2 && lowcall == 0) {
            curDire = 1;
        }
    }

    private boolean hasOut() {
        return (available & destination & (1 << curFloor.get())) != 0;
    }

    private boolean hasIn() {
        if (curNum.get() == maxMem) {
            return false;
        }
        if (curDire == 0) {
            return (available & (bitDown | bitUp) & (1 << curFloor.get())) != 0;
        } else {
            if (curDire == 1) {
                int highcall = (destination | bitDown | bitUp) >> (curFloor.get() + 1);
                if ((available & bitUp & (1 << curFloor.get())) != 0) {
                    return true;
                } else if (highcall == 0 && (available & bitDown & (1 << curFloor.get())) != 0) {
                    curDire = 2;
                    return true;
                }
            } else {
                int lowcall = (destination | bitDown | bitUp) & ((1 << curFloor.get()) - 1);
                if ((available & bitDown & (1 << curFloor.get())) != 0) {
                    return true;
                } else if (lowcall == 0 && (available & bitUp & (1 << curFloor.get())) != 0) {
                    curDire = 1;
                    return true;
                }
            }
            return false;
        }
    }

    private void open(boolean onlyIn) {
        try {
            onServe.get(curFloor.get()).acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (onlyIn) {
            try {
                onlyLetIn.get(curFloor.get()).acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        TimableOutput.println(String.format("OPEN-%d-%d", curFloor.get() + 1, id));
        this.lasttime = System.currentTimeMillis();
    }

    private void close(boolean onlyIn) {
        try {
            long curTime = System.currentTimeMillis();
            if (this.lasttime + openCost + closeCost > curTime) {
                sleep(this.lasttime + openCost + closeCost - curTime);
            }
            TimableOutput.println(String.format("CLOSE-%d-%d", curFloor.get() + 1, id));
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            onServe.get(curFloor.get()).release();
            if (onlyIn) {
                onlyLetIn.get(curFloor.get()).release();
            }
        }
        this.lasttime = System.currentTimeMillis();
    }

    private void letIn() {
        if (curDire == 0) {
            if (curFloor.get() > 6) {
                curDire = bitUp != 0 ? 1 : 2;
            } else {
                curDire = bitDown != 0 ? 2 : 1;
            }
        }
        ArrayList<Person> templist = curDire == 1 ? waitUp : waitDown;
        try {
            sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        stautsLock.lock();
        try {
            int tempbit = curDire == 1 ? bitUp : bitDown;
            Iterator<Person> iterator = templist.iterator();
            while (iterator.hasNext() && curNum.get() < maxMem) {
                Person per = iterator.next();
                if (per.getCurFloor() == curFloor.get()) {
                    onElevator.add(per);
                    TimableOutput.println(
                        String.format("IN-%d-%d-%d", per.getId(), curFloor.get() + 1, id)
                    );
                    destination |= (1 << per.getTempDest());
                    curNum.incrementAndGet();
                    iterator.remove();
                }
            }
            tempbit ^= (1 << curFloor.get());
            while (iterator.hasNext()) {
                Person per = iterator.next();
                if (per.getCurFloor() == curFloor.get()) {
                    tempbit |= (1 << curFloor.get());
                    break;
                }
            }
            if (curDire == 1) {
                bitUp = tempbit;
            } else {
                bitDown = tempbit;
            }
        } finally {
            stautsLock.unlock();
        }
    }

    private void letOut() {
        Iterator<Person> iterator = onElevator.iterator();
        while (iterator.hasNext()) {
            Person person = iterator.next();
            if (person.getTempDest() == curFloor.get()) {
                TimableOutput.println(String.format("OUT-%d-%d-%d",
                    person.getId(), curFloor.get() + 1, id));
                if (person.getDestFloor() != curFloor.get()) {
                    person.setCurFloor(curFloor.get());
                    table.add(person);
                } else {
                    table.personArrive();
                }
                iterator.remove();
                curNum.decrementAndGet();
            }
        }
        destination ^= (1 << curFloor.get());
    }

    private void move() {
        if ((curDire == 1 && curFloor.get() == 10) ||
            (curDire == 2 && curFloor.get() == 0) ||
            curDire == 0) {
            return;
        }
        if (curDire == 1) {
            curFloor.incrementAndGet();
        } else {
            curFloor.decrementAndGet();
        }
        try {
            long curTime = System.currentTimeMillis();
            if (this.lasttime + moveCost > curTime) {
                sleep(lasttime + moveCost - curTime);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        TimableOutput.println(String.format("ARRIVE-%d-%d", curFloor.get() + 1, id));
        this.lasttime = System.currentTimeMillis();
    }

    private void maintain() {
        stautsLock.lock();
        try {
            if (onElevator.size() != 0) {
                open(false);
                onElevator.forEach(person -> {
                    TimableOutput.println(String.format("OUT-%d-%d-%d",
                        person.getId(), curFloor.get() + 1, id));
                    if (person.getDestFloor() != curFloor.get()) {
                        person.setCurFloor(curFloor.get());
                        table.add(person);
                    } else {
                        table.personArrive();
                    }
                });
                close(false);
            }
            waitUp.forEach(person -> {
                table.add(person);
            });
            waitDown.forEach(person -> {
                table.add(person);
            });
            TimableOutput.println(String.format("MAINTAIN_ABLE-%d", id));
        } finally {
            stautsLock.unlock();
        }
    }
}