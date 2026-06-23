package edu.homework.inspection.common;

public class NavigatorTask {
    private String carId;
    private int startx;
    private int starty;

    public NavigatorTask() {
    }

    public NavigatorTask(String carId, int startx, int starty) {
        this.carId = carId;
        this.startx = startx;
        this.starty = starty;
    }

    public String getCarId() {
        return carId;
    }

    public void setCarId(String carId) {
        this.carId = carId;
    }

    public int getStartx() {
        return startx;
    }

    public void setStartx(int startx) {
        this.startx = startx;
    }

    public int getStarty() {
        return starty;
    }

    public void setStarty(int starty) {
        this.starty = starty;
    }

    public int carNumber() {
        if (carId == null || !carId.startsWith(Keys.CARS_PREFIX)) {
            throw new IllegalArgumentException("Unsupported car id: " + carId);
        }
        return Integer.parseInt(carId.substring(Keys.CARS_PREFIX.length()));
    }

    @Override
    public String toString() {
        return "NavigatorTask{" +
                "carId='" + carId + '\'' +
                ", startx=" + startx +
                ", starty=" + starty +
                '}';
    }
}
