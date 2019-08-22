package cn.autolabor.transform

class TransformSystem<Key> {
    private val graphic = hashMapOf<Pair<Key, Key>, HashMap<Long, Transformation>>()
}
