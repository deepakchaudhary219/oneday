package oneday.discovery;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Story Map privacy thresholds and size.
 *
 * @param neighbourhoodK distinct people a ~0.7 km² cell needs before it is shown as its own cluster
 * @param areaK distinct people a ~5 km area needs before it is placed at all
 * @param maxClusters clusters per response (a map, not an infinite feed)
 * @param storiesPerCluster story cards per cluster
 */
@ConfigurationProperties("oneday.map")
public record StoryMapProperties(int neighbourhoodK, int areaK, int maxClusters, int storiesPerCluster) {
}
