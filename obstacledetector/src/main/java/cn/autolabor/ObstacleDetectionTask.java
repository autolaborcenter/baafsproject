package cn.autolabor;

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

import static cn.autolabor.GeometricUtil.convexHull;

@SuppressWarnings("WeakerAccess")
@TaskProperties(name = "ObstacleDetectionTask")
public class ObstacleDetectionTask extends AbstractTask {

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
    @InjectMessage(topic = "obstacle_points")
    MessageHandle<List<Msg2DPoint>> obstaclePointsHandle;
    @InjectMessage(topic = "${obstaclesTopic}")
    MessageHandle<List<MsgPolygon>> obstaclesHandle;
    @TaskParameter(name = "obstaclesTopic", value = "obstacles")
    private String obstaclesTopic;

    private Map<String, LidarInfo> lidarInfos = new HashMap<>(); // key -> frame
    private Map<String, MsgLidar> lidarData = new HashMap<>(); // key -> topic
    private Map<String, Long> lastTimeMap = new HashMap<>(); // key -> topic


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

        List<MsgPolygon> obstacles = new ArrayList<>();
        for (int i = 1; i < clusterPoints.size(); i++) {
            obstacles.add(new MsgPolygon(baseLinkFrame, convexHull(clusterPoints.get(i))));
        }

        for (int j = 0; j < clusterPoints.get(0).size(); j++) {
            List<Msg2DPoint> singleObstacle = new ArrayList<>();
            singleObstacle.add(clusterPoints.get(0).get(j));
            obstacles.add(new MsgPolygon(baseLinkFrame, singleObstacle));
        }
        obstaclesHandle.pushSubData(obstacles);
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

        @Override
        public String toString() {
            return "LidarInfo{" +
                    "x=" + x +
                    ", y=" + y +
                    ", theta=" + theta +
                    ", reverse=" + reverse +
                    '}';
        }
    }
}
