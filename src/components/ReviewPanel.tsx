import { Check, RefreshCw, Sparkles } from 'lucide-react-native';
import { Image, Pressable, ScrollView, Text, View } from 'react-native';

import { featuredSpeciesIds, speciesImages } from '../data/speciesImages';
import { colors, radius, shadow } from '../styles/theme';
import type { ReviewObservation } from '../lib/reviewApi';

type ReviewPanelProps = {
  reviews: ReviewObservation[];
  isLoading: boolean;
  onRefresh: () => void;
  onConfirm: (observation: ReviewObservation, speciesId: string) => void;
};

export function ReviewPanel({ reviews, isLoading, onRefresh, onConfirm }: ReviewPanelProps) {
  return (
    <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
      <View style={styles.headerRow}>
        <View style={{ flex: 1 }}>
          <Text style={styles.heading}>これはだれかな？</Text>
          <Text style={styles.subheading}>写真を見て、いちばん近い候補を選ぼう</Text>
        </View>
        <Pressable onPress={onRefresh} style={styles.refreshButton} accessibilityLabel="AI候補を更新">
          <RefreshCw color={colors.forest} size={20} />
        </Pressable>
      </View>

      {reviews.length === 0 ? (
        <View style={styles.empty}>
          <View style={styles.emptyImages}>
            {featuredSpeciesIds.slice(0, 3).map((id) => (
              <Image key={id} source={speciesImages[id]} style={styles.emptyImage} />
            ))}
          </View>
          <View style={styles.emptySparkle}><Sparkles color={colors.amber} size={23} /></View>
          <Text style={styles.emptyTitle}>{isLoading ? '新しい写真を探しています' : '確認する写真はありません'}</Text>
          <Text style={styles.emptyText}>THINKLETで撮った写真をAIが調べると、ここに候補が届きます。</Text>
          <Pressable onPress={onRefresh} style={styles.emptyButton}>
            <RefreshCw color={colors.white} size={17} />
            <Text style={styles.emptyButtonText}>{isLoading ? '確認中...' : 'もう一度確認'}</Text>
          </Pressable>
        </View>
      ) : null}

      {reviews.map((review, reviewIndex) => (
        <View key={review.id} style={styles.reviewBlock}>
          <View style={styles.photoWrap}>
            {review.imageUri ? <Image source={{ uri: review.imageUri }} style={styles.photo} /> : null}
            <View style={styles.photoBadge}>
              <Sparkles color={colors.white} size={14} />
              <Text style={styles.photoBadgeText}>AIが調べました</Text>
            </View>
            <View style={styles.photoCount}><Text style={styles.photoCountText}>{reviewIndex + 1}/{reviews.length}</Text></View>
          </View>

          <View style={styles.observationMeta}>
            <Text style={styles.observationMetaTitle}>THINKLETから届いた写真</Text>
            <Text style={styles.observationMetaText}>{new Date(review.capturedAt).toLocaleString('ja-JP')}</Text>
          </View>

          <Text style={styles.candidateHeading}>どれに見える？</Text>
          <View style={styles.candidateList}>
            {review.candidates.map((candidate, index) => {
              const image = candidate.candidate ? speciesImages[candidate.candidate.id] : null;
              return (
                <Pressable
                  key={candidate.speciesId}
                  onPress={() => onConfirm(review, candidate.speciesId)}
                  style={({ pressed }) => [styles.candidateRow, index === 0 && styles.candidateRowTop, pressed && styles.pressed]}
                >
                  {image ? (
                    <Image source={image} style={styles.candidateImage} />
                  ) : (
                    <View style={styles.candidatePlaceholder}><Sparkles color={colors.leaf} size={22} /></View>
                  )}
                  <View style={styles.candidateBody}>
                    <View style={styles.candidateTitleRow}>
                      <Text style={styles.candidateName}>{candidate.candidate?.commonName ?? 'まだ名前のない候補'}</Text>
                      {index === 0 ? <Text style={styles.bestBadge}>いちばん近い</Text> : null}
                    </View>
                    <Text style={styles.candidateKind}>{candidate.candidate?.category === 'plant' ? '植物' : '昆虫'}</Text>
                    <Text style={styles.reason} numberOfLines={2}>{candidate.reason}</Text>
                  </View>
                  <View style={styles.checkButton}><Check color={colors.white} size={19} strokeWidth={3} /></View>
                </Pressable>
              );
            })}
          </View>
          <Text style={styles.helper}>選んだ生き物だけが図鑑に登録されます。</Text>
        </View>
      ))}
    </ScrollView>
  );
}

const styles = {
  scrollContent: { padding: 18, paddingBottom: 112, gap: 16 },
  headerRow: { flexDirection: 'row' as const, alignItems: 'center' as const, gap: 14 },
  heading: { color: colors.ink, fontSize: 22, fontWeight: '900' as const },
  subheading: { color: colors.inkSoft, fontSize: 12, lineHeight: 18, marginTop: 4 },
  refreshButton: { width: 42, height: 42, borderRadius: radius.medium, backgroundColor: colors.white, borderWidth: 1, borderColor: colors.line, alignItems: 'center' as const, justifyContent: 'center' as const },
  empty: { marginTop: 16, padding: 20, borderRadius: radius.medium, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.white, alignItems: 'center' as const },
  emptyImages: { flexDirection: 'row' as const, marginTop: 3 },
  emptyImage: { width: 78, height: 78, borderRadius: 39, borderWidth: 4, borderColor: colors.white, marginHorizontal: -8, backgroundColor: colors.line },
  emptySparkle: { width: 44, height: 44, borderRadius: 22, backgroundColor: colors.amberPale, alignItems: 'center' as const, justifyContent: 'center' as const, marginTop: -16 },
  emptyTitle: { color: colors.ink, fontSize: 18, fontWeight: '900' as const, marginTop: 12 },
  emptyText: { color: colors.inkSoft, fontSize: 12, lineHeight: 19, textAlign: 'center' as const, maxWidth: 340, marginTop: 7 },
  emptyButton: { minHeight: 44, marginTop: 16, borderRadius: radius.medium, paddingHorizontal: 16, backgroundColor: colors.forest, flexDirection: 'row' as const, alignItems: 'center' as const, justifyContent: 'center' as const, gap: 7 },
  emptyButtonText: { color: colors.white, fontSize: 13, fontWeight: '900' as const },
  reviewBlock: { gap: 12 },
  photoWrap: { position: 'relative' as const },
  photo: { width: '100%' as const, aspectRatio: 1.2, borderRadius: radius.medium, backgroundColor: colors.line },
  photoBadge: { position: 'absolute' as const, left: 10, top: 10, borderRadius: 6, paddingHorizontal: 9, paddingVertical: 6, backgroundColor: 'rgba(36, 91, 57, 0.88)', flexDirection: 'row' as const, alignItems: 'center' as const, gap: 5 },
  photoBadgeText: { color: colors.white, fontSize: 10, fontWeight: '900' as const },
  photoCount: { position: 'absolute' as const, right: 10, top: 10, borderRadius: 6, paddingHorizontal: 8, paddingVertical: 6, backgroundColor: 'rgba(20, 45, 33, 0.74)' },
  photoCountText: { color: colors.white, fontSize: 10, fontWeight: '900' as const },
  observationMeta: { padding: 12, borderRadius: radius.medium, backgroundColor: colors.leafPale, flexDirection: 'row' as const, alignItems: 'center' as const, gap: 8 },
  observationMetaTitle: { flex: 1, color: colors.ink, fontSize: 12, fontWeight: '900' as const },
  observationMetaText: { color: colors.inkSoft, fontSize: 10 },
  candidateHeading: { color: colors.ink, fontSize: 17, fontWeight: '900' as const, marginTop: 3 },
  candidateList: { gap: 9 },
  candidateRow: { minHeight: 104, borderRadius: radius.medium, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.white, padding: 9, flexDirection: 'row' as const, alignItems: 'center' as const, gap: 10, ...shadow },
  candidateRowTop: { borderColor: '#B9D5B3', backgroundColor: '#FAFDF8' },
  candidateImage: { width: 82, height: 82, borderRadius: radius.small, backgroundColor: colors.line },
  candidatePlaceholder: { width: 82, height: 82, borderRadius: radius.small, backgroundColor: colors.leafPale, alignItems: 'center' as const, justifyContent: 'center' as const },
  candidateBody: { flex: 1, gap: 3 },
  candidateTitleRow: { flexDirection: 'row' as const, alignItems: 'center' as const, gap: 6 },
  candidateName: { flexShrink: 1, color: colors.ink, fontSize: 15, fontWeight: '900' as const },
  bestBadge: { overflow: 'hidden' as const, borderRadius: 5, backgroundColor: colors.leafPale, color: colors.forest, paddingHorizontal: 6, paddingVertical: 3, fontSize: 8, fontWeight: '900' as const },
  candidateKind: { color: colors.forest, fontSize: 10, fontWeight: '800' as const },
  reason: { color: colors.inkSoft, fontSize: 11, lineHeight: 16, marginTop: 2 },
  checkButton: { width: 34, height: 34, borderRadius: 17, backgroundColor: colors.forest, alignItems: 'center' as const, justifyContent: 'center' as const },
  helper: { color: colors.inkSoft, fontSize: 11, textAlign: 'center' as const, marginTop: 2 },
  pressed: { opacity: 0.7 },
};
