package io.github.jicklin.starter.ratelimit.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 参数分组信息
 * 用于封装分组相关的信息
 *
 * @author marry
 */
public class ParamGroupInfo {

    /**
     * 分组名称（基础名称，如"products"）
     */
    private final String groupName;

    /**
     * 实际分组名称（包含内容标识，如"products_PROD001"）
     */
    private String actualGroupName;

    /**
     * 分组权重
     */
    private final int weight;

    /**
     * 分组参数
     * key: 参数名
     * value: 参数值
     */
    private final Map<String, Object> params;

    /**
     * 构造函数
     *
     * @param groupName 分组名称
     * @param weight 分组权重
     */
    public ParamGroupInfo(String groupName, int weight) {
        this.groupName = groupName;
        this.actualGroupName = groupName; // 默认与基础名称相同
        this.weight = weight;
        this.params = new TreeMap<>();
    }

    /**
     * 构造函数（带实际分组名称）
     *
     * @param groupName 基础分组名称
     * @param actualGroupName 实际分组名称
     * @param weight 分组权重
     */
    public ParamGroupInfo(String groupName, String actualGroupName, int weight) {
        this.groupName = groupName;
        this.actualGroupName = actualGroupName;
        this.weight = weight;
        this.params = new TreeMap<>();
    }

    /**
     * 获取基础分组名称
     */
    public String getGroupName() {
        return groupName;
    }

    /**
     * 获取实际分组名称
     */
    public String getActualGroupName() {
        return actualGroupName;
    }

    /**
     * 设置实际分组名称
     */
    public void setActualGroupName(String actualGroupName) {
        this.actualGroupName = actualGroupName;
    }

    /**
     * 获取分组权重
     */
    public int getWeight() {
        return weight;
    }

    /**
     * 获取分组参数
     */
    public Map<String, Object> getParams() {
        return params;
    }

    /**
     * 添加参数
     *
     * @param paramName 参数名
     * @param paramValue 参数值
     */
    public void addParam(String paramName, Object paramValue) {
        params.put(paramName, paramValue);
    }

    /**
     * 是否为空分组
     */
    public boolean isEmpty() {
        return params.isEmpty();
    }

    /**
     * 是否为默认分组
     */
    public boolean isDefaultGroup() {
        return groupName == null || groupName.isEmpty();
    }

    /**
     * 获取分组参数哈希
     */
    public String getParamsHash() {
        return params.toString();
    }

    /**
     * 按权重排序比较器
     */
    public static final Comparator<ParamGroupInfo> WEIGHT_COMPARATOR =
        (g1, g2) -> Integer.compare(g2.getWeight(), g1.getWeight()); // 权重高的排前面

    /**
     * 按分组名称排序比较器
     */
    public static final Comparator<ParamGroupInfo> NAME_COMPARATOR =
        Comparator.comparing(ParamGroupInfo::getGroupName,
            Comparator.nullsFirst(String::compareTo));

    /**
     * 创建分组列表
     *
     * @param orderByWeight 是否按权重排序
     * @return 排序后的分组列表
     */
    public static List<ParamGroupInfo> createSortedList(List<ParamGroupInfo> groups, boolean orderByWeight) {
        List<ParamGroupInfo> result = new ArrayList<>(groups);

        // 过滤掉空分组
        result.removeIf(ParamGroupInfo::isEmpty);

        // 按权重或名称排序
        if (orderByWeight) {
            result.sort(WEIGHT_COMPARATOR.thenComparing(NAME_COMPARATOR));
        } else {
            result.sort(NAME_COMPARATOR);
        }

        return result;
    }

    @Override
    public String toString() {
        return "Group{" +
                "name='" + groupName + '\'' +
                ", actualName='" + actualGroupName + '\'' +
                ", weight=" + weight +
                ", params=" + params.size() +
                '}';
    }
}
