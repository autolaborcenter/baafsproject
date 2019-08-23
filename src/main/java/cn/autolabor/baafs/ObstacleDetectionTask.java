package cn.autolabor.baafs;

import cn.autolabor.core.annotation.InjectMessage;
import cn.autolabor.core.annotation.TaskFunction;
import cn.autolabor.core.annotation.TaskParameter;
import cn.autolabor.core.annotation.TaskProperties;
import cn.autolabor.core.server.ServerManager;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.core.server.message.MessageHandle;
import cn.autolabor.core.server.message.MessageSource;
import cn.autolabor.core.server.message.MessageSourceType;
import cn.autolabor.message.navigation.Msg2DPoint;
import cn.autolabor.message.navigation.MsgPolygon;
import cn.autolabor.message.sensor.MsgLidar;
import cn.autolabor.util.reflect.TypeNode;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.Math.max;
import static java.lang.Math.min;

@SuppressWarnings("WeakerAccess")
@TaskProperties(name = "ObstacleDetectionTask")
public class ObstacleDetectionTask extends AbstractTask {

    @InjectMessage(topic = "obstacle_points")
    MessageHandle<List<Msg2DPoint>> obstaclePointsHandle;
    @InjectMessage(topic = "obstacles")
    MessageHandle<List<MsgPolygon>> obstaclesHandle;
    @TaskParameter(name = "clusterMinPoints", value = "2")
    private int clusterMinPoints;
    @TaskParameter(name = "clusterMaxPoints", value = "30")
    private int clusterMaxPoints;
    @TaskParameter(name = "clusterMaxDistance", value = "0.4")
    private double clusterMaxDistance;
    private double clusterMaxDistance2;
    @TaskParameter(name = "baseLinkFrame", value = "baseLink")
    private String baseLinkFrame;
    @TaskParameter(name = "lidarTopics", value = "[\"faselase\"]")
    private List<String> lidarTopics;
    @TaskParameter(name = "timeout", value = "50")
    private double timeout;
    private Map<String, LidarInfo> lidarInfos = new HashMap<>();
    private Map<String, MsgLidar> lidarData = new HashMap<>();
    private Map<String, Long> lastTimeMap = new HashMap<>();

    private List<MsgPolygon> obstacles = new ArrayList<>();

    public ObstacleDetectionTask(String... name) {
        super(name);
        clusterMaxDistance2 = clusterMaxDistance * clusterMaxDistance;
        //noinspection unchecked
        ((Map<String, Map>) ServerManager.me().getConfig("urdf", baseLinkFrame))
            .forEach((key, value) -> {
                LidarInfo lidarInfo = new LidarInfo(
                    (Double) value.get("x"),
                    (Double) value.get("y"),
                    (Double) value.get("theta"),
                    (Boolean) value.get("reverse")
                );
                lidarInfos.put(key, lidarInfo);
            });

        lidarTopics.forEach(topic -> ServerManager.me().getOrCreateMessageHandle(topic, new TypeNode(MsgLidar.class)).addCallback(this, "mergeLidarData", new MessageSourceType[]{}));
    }

    public static void main(String[] args) {
        ServerManager.me().register(new ObstacleDetectionTask());
    }

    @TaskFunction(name = "mergeLidarData")
    public void mergeLidarData(MsgLidar msgLidar, MessageSource source) {
        Long currentTime = System.currentTimeMillis();
        if (lidarInfos.containsKey(msgLidar.getHeader().getCoordinate())) {
            lidarData.put(source.getTopic(), msgLidar);
            lastTimeMap.put(source.getTopic(), currentTime);
        }

        if (lastTimeMap.size() == lidarTopics.size() && lastTimeMap.values().stream().allMatch(lastTime -> (currentTime - lastTime) <= timeout)) {
            asyncRun("updateObstaclePoints");
        }
    }

    @TaskFunction(name = "updateObstaclePoints")
    public void updateObstaclePoints() {
        // 雷达数据转障碍物点
        List<Msg2DPoint> obstaclePoints =
            lidarData
                .values()
                .stream()
                .flatMap(value -> {
                    LidarInfo info = lidarInfos.get(value.getHeader().getCoordinate());
                    return IntStream
                        .range(0, value.getAngles().size())
                        .mapToObj(i ->
                            info.transform(
                                value.getAngles().get(i),
                                value.getDistances().get(i)
                            ));
                })
                .collect(Collectors.toList());

        obstaclePointsHandle.pushSubData(obstaclePoints);

        List<List<Msg2DPoint>> clusterPoints = cluster(obstaclePoints);
        obstacles.clear();
        for (int i = 1; i < clusterPoints.size(); i++) {
            obstacles.add(new MsgPolygon(baseLinkFrame, convexHull(clusterPoints.get(i))));
        }

        for (int j = 0; j < clusterPoints.get(0).size(); j++) {
            List<Msg2DPoint> singleObstacle = new ArrayList<>();
            singleObstacle.add(clusterPoints.get(0).get(j));
            obstacles.add(new MsgPolygon(baseLinkFrame, singleObstacle));
        }
        obstaclesHandle.pushSubData(obstacles);
//        for (int i = 1; i < obstacles.size(); i++) {
//            MsgPolygon polygon = obstacles.get(i);
//            for (int j = 0; j < polygon.getPoints().size(); j++) {
//                System.out.println(String.format("%5.4f, %5.4f", polygon.getPoints().get(j).getX(), polygon.getPoints().get(j).getY()));
//            }
//            System.out.println("========");
//        }
    }

    @TaskFunction(name = "detectCollision")
    public boolean detectCollision(MsgPolygon checkData) {
        List<Msg2DPoint> checkList = checkData.getPoints();
        for (int i = 0; i < obstacles.size(); i++) {
            List<Msg2DPoint> obstacle = obstacles.get(i).getPoints();
            if (i == 0) {
                // 单独离散点
                if (obstacle.stream().anyMatch(p -> checkPointInside(p, checkList))) {
                    return true;
                }
            } else {
                // 非离散点情况
                // 判断包含关系
                boolean insideFlag = checkList.stream().noneMatch(p -> checkPointInside(p, obstacle)) && obstacle.stream().noneMatch(p -> checkPointInside(p, checkList));
                if (insideFlag) {
                    return true;
                }
                // 判断线相交关系
                if (obstacle.size() > 2 && checkList.size() > 2) {
                    int p, s;
                    for (p = 0; p < obstacle.size() - 1; p++) {
                        for (s = 0; s < checkList.size() - 1; s++) {
                            if (checkLineCross(obstacle.get(p), obstacle.get(p + 1), checkList.get(s), checkList.get(s + 1))) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean checkLineCross(Msg2DPoint a, Msg2DPoint b, Msg2DPoint c, Msg2DPoint d) {
        if (max(c.getX(), d.getX()) < min(a.getX(), d.getX()) || max(c.getY(), d.getY()) < min(a.getY(), b.getY()) || max(a.getX(), b.getX()) < min(c.getX(), d.getX()) || max(a.getY(), b.getY()) < min(c.getY(), d.getY())) {
            return false;
        } else {
            return (cross(a, c, d) * cross(b, c, d) <= 0) && (cross(c, a, b) * cross(d, a, b) <= 0);
        }
    }

    private boolean checkPointInside(Msg2DPoint point, List<Msg2DPoint> path) {
        boolean result = false;
        if (path.size() > 2) {
            for (int i = 0; i < path.size() - 1; i++) {
                if ((path.get(i).getY() > point.getY()) != (path.get(i + 1).getY() > point.getY()) && (point.getX() < (path.get(i + 1).getX() - path.get(i).getX()) * (point.getY() - path.get(i).getY()) / (path.get(i + 1).getY() - path.get(i).getY()) + path.get(i).getX())) {
                    result = !result;
                }
            }
            return result;
        } else if (path.size() == 1) {
            Msg2DPoint pathPoint = path.get(0);
            return (pathPoint.getX() == point.getX()) && (pathPoint.getY() == point.getY());
        } else {
            return false;
        }
    }

    private List<List<Msg2DPoint>> cluster(List<Msg2DPoint> points) {
        List<List<Msg2DPoint>> clusters = new ArrayList<>();
        Boolean[] visited = new Boolean[points.size()];
        Arrays.fill(visited, false);

        int clusterId = 0;
        clusters.add(new ArrayList<>());

        for (int i = 0; i < points.size(); i++) {
            if (!visited[i]) {
                visited[i] = true;
                List<Integer> neighbors = regionQuery(points, i);
                if (neighbors.size() < clusterMinPoints) { // noise
                    clusters.get(0).add(points.get(i));
                } else {
                    // add cluster
                    clusterId += 1;
                    clusters.add(new ArrayList<>());
                    // add first point
                    clusters.get(clusterId).add(points.get(i));
                    // search from this point
                    LinkedList<Integer> neighborQueue = new LinkedList<>(neighbors);
                    while (!neighborQueue.isEmpty()) {
                        if (clusters.get(clusterId).size() == clusterMaxPoints) {
                            break;
                        }

                        int neighborIndex = neighborQueue.poll();
                        if (!visited[neighborIndex]) {
                            visited[neighborIndex] = true;

                            List<Integer> furtherNeighbors = regionQuery(points, neighborIndex);
                            if (furtherNeighbors.size() >= clusterMinPoints) {
                                neighborQueue.addAll(furtherNeighbors);
                                clusters.get(clusterId).add(points.get(neighborIndex));
                            }
                        }
                    }
                }
            }
        }
        return clusters;

    }

    private List<Msg2DPoint> convexHull(List<Msg2DPoint> points) {
        LinkedList<Msg2DPoint> polygon = new LinkedList<>();
        points.sort(Comparator.comparingDouble(Msg2DPoint::getX).thenComparingDouble(Msg2DPoint::getY));

        int i = 0;
        // 找左侧边值点
        int minmin = 0;
        int minmax = 0;
        double xmin = points.get(0).getX();
        for (i = 1; i < points.size(); i++) {
            if (points.get(i).getX() != xmin) {
                break;
            }
        }
        minmax = i - 1;
        // 一条线上
        if (minmax == points.size() - 1) {
            polygon.add(points.get(minmin));
            if (points.get(minmax).getY() != points.get(minmin).getY()) {
                polygon.add(points.get(minmax));
            }
            polygon.add(points.get(minmin));
        }
        // 找右侧边值点
        int maxmin = points.size() - 1;
        int maxmax = maxmin;
        double xmax = points.get(points.size() - 1).getX();
        for (i = points.size() - 2; i >= 0; i--) {
            if (points.get(i).getX() != xmax) {
                break;
            }
        }
        maxmin = i + 1;
        // 找下边界凸轮廓
        polygon.add(points.get(minmin));
        for (i = minmax + 1; i <= maxmin; i++) {
            // 下边界以上数据剔除
            if (cross(points.get(minmin), points.get(maxmin), points.get(i)) >= 0 && i < maxmin) {
                continue;
            }

            while (polygon.size() > 1) {
                if (cross(polygon.get(polygon.size() - 2), polygon.getLast(), points.get(i)) > 0) {
                    break;
                }
                polygon.removeLast();
            }
            polygon.add(points.get(i));
        }
        // 连接maxmax
        if (maxmax != maxmin) {
            polygon.add(points.get(maxmax));
        }
        // 找上边界凸轮廓
        int bot = polygon.size();
        for (i = maxmin - 1; i >= minmax; i--) {
            if (cross(points.get(maxmax), points.get(minmax), points.get(i)) >= 0 && i > minmax) {
                continue;
            }

            while (polygon.size() > bot) {
                if (cross(polygon.get(polygon.size() - 2), polygon.getLast(), points.get(i)) > 0) {
                    break;
                }
                polygon.removeLast();
            }

            polygon.add(points.get(i));
        }
        // 连接minmin
        if (minmax != minmin) {
            polygon.add(points.get(minmin));
        }

        return polygon;
    }

    private double cross(Msg2DPoint o, Msg2DPoint a, Msg2DPoint b) {
        return (a.getX() - o.getX()) * (b.getY() - o.getY()) - (a.getY() - o.getY()) * (b.getX() - o.getX());
    }

    private List<Integer> regionQuery(List<Msg2DPoint> points, int index) {
        List<Integer> neighbors = new ArrayList<>();
        double x = points.get(index).getX();
        double y = points.get(index).getY();
        for (int i = 0; i < points.size(); i++) {
            if (i == index) {
                continue;
            }

            Msg2DPoint point = points.get(i);
            double distance = Math.pow(point.getX() - x, 2) + Math.pow(point.getY() - y, 2);
            if (distance != 0 && distance <= clusterMaxDistance2) {
                neighbors.add(i);
            }
        }
        return neighbors;
    }

    class LidarInfo {
        private double x;
        private double y;
        private double theta;
        private boolean reverse;

        public LidarInfo(double x, double y, double theta, boolean reverse) {
            this.x = x;
            this.y = y;
            this.theta = theta;
            this.reverse = reverse;
        }

        public Msg2DPoint transform(double angle, double distance) {
            angle = reverse ? -angle : angle;
            Msg2DPoint msg2DPoint = new Msg2DPoint();
            double x = distance * Math.cos(angle);
            double y = distance * Math.sin(angle);
            msg2DPoint.setX(this.x + x * Math.cos(this.theta) - y * Math.sin(this.theta));
            msg2DPoint.setY(this.y + x * Math.sin(this.theta) + y * Math.cos(this.theta));
            return msg2DPoint;
        }
    }
}
