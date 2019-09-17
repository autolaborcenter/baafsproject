package cn.autolabor;

import cn.autolabor.message.navigation.Msg2DPoint;
import cn.autolabor.message.navigation.MsgPolygon;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class GeometricUtil {

    public static double cross(Msg2DPoint o, Msg2DPoint a, Msg2DPoint b) {
        return (a.getX() - o.getX()) * (b.getY() - o.getY()) - (a.getY() - o.getY()) * (b.getX() - o.getX());
    }

    public static boolean checkPointInside(Msg2DPoint point, List<Msg2DPoint> path) {
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

    public static boolean checkLineCross(Msg2DPoint a, Msg2DPoint b, Msg2DPoint c, Msg2DPoint d) {
        if (Math.max(c.getX(), d.getX()) < Math.min(a.getX(), d.getX()) || Math.max(c.getY(), d.getY()) < Math.min(a.getY(), b.getY()) || Math.max(a.getX(), b.getX()) < Math.min(c.getX(), d.getX()) || Math.max(a.getY(), b.getY()) < Math.min(c.getY(), d.getY())) {
            return false;
        } else {
            return (cross(a, c, d) * cross(b, c, d) <= 0) && (cross(c, a, b) * cross(d, a, b) <= 0);
        }
    }

    public static boolean detectCollision(List<Msg2DPoint> p1, List<Msg2DPoint> p2) {
        if (null != p1 && p1.size() > 0 && null != p2 && p2.size() > 0) {
            if (p1.size() == 1) {
                return checkPointInside(p1.get(0), p2);
            }

            if (p2.size() == 1) {
                return checkPointInside(p2.get(0), p1);
            }

            if (p1.stream().anyMatch(p -> checkPointInside(p, p2)) && p2.stream().anyMatch(p -> checkPointInside(p, p1))) {
                return true;
            } else {
                for (int i = 0; i < p1.size() - 1; i++) {
                    for (int j = 0; j < p2.size() - 1; j++) {
                        if (checkLineCross(p1.get(i), p1.get(i + 1), p2.get(j), p2.get(j + 1))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static boolean detectCollision(MsgPolygon p1, MsgPolygon p2) {
        if (null != p1 && null != p2) {
            return detectCollision(p1.getPoints(), p2.getPoints());
        }
        return false;
    }

    public static boolean detectCollision(List<MsgPolygon> polygons, MsgPolygon p) {
        if (null != polygons && null != p) {
            return polygons.stream().anyMatch(p1 -> detectCollision(p1, p));
        }
        return false;
    }

    public static List<Msg2DPoint> convexHull(List<Msg2DPoint> points) {
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

}
