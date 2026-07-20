import { Image, Pressable, ScrollView, Text, View } from 'react-native';

import type { ReviewObservation } from '../lib/reviewApi';

type ReviewPanelProps = {
  reviews: ReviewObservation[];
  isLoading: boolean;
  onRefresh: () => void;
  onConfirm: (observation: ReviewObservation, speciesId: string) => void;
};

export function ReviewPanel({
  reviews,
  isLoading,
  onRefresh,
  onConfirm,
}: ReviewPanelProps) {
  return (
    <ScrollView contentContainerStyle={{ paddingBottom: 120 }}>
      <View style={{ gap: 14 }}>
        <View style={styles.hero}>
          <Text style={styles.kicker}>AI CARD BOX</Text>
          <Text style={styles.title}>これはだれかな？</Text>
          <Pressable onPress={onRefresh} style={styles.refreshButton}>
            <Text style={styles.refreshText}>{isLoading ? 'さがし中...' : '新しい写真をさがす'}</Text>
          </Pressable>
        </View>

        {reviews.length === 0 ? (
          <View style={styles.empty}>
            <Text style={styles.emptyTitle}>候補カードはまだありません</Text>
            <Text style={styles.emptyText}>THINKLETで撮ると、ここにAIのよそうが届きます。</Text>
          </View>
        ) : null}

        {reviews.map((review) => (
          <View key={review.id} style={styles.reviewCard}>
            {review.imageUri ? <Image source={{ uri: review.imageUri }} style={styles.photo} /> : null}
            <Text style={styles.time}>{new Date(review.capturedAt).toLocaleString('ja-JP')}</Text>
            <View style={styles.candidateGrid}>
              {review.candidates.map((candidate) => (
                <Pressable
                  key={candidate.speciesId}
                  onPress={() => onConfirm(review, candidate.speciesId)}
                  style={({ pressed }) => [styles.candidateCard, pressed && { opacity: 0.75 }]}
                >
                  <Text style={styles.candidateName}>
                    {candidate.candidate?.commonName ?? 'なぞの候補'}
                  </Text>
                  <Text style={styles.candidateKind}>
                    {candidate.candidate?.category === 'plant' ? '植物' : '虫'}
                  </Text>
                  <Text style={styles.reason} numberOfLines={3}>
                    {candidate.reason}
                  </Text>
                  <Text style={styles.pick}>これにする</Text>
                </Pressable>
              ))}
            </View>
          </View>
        ))}
      </View>
    </ScrollView>
  );
}

const styles = {
  hero: {
    borderRadius: 8,
    padding: 16,
    backgroundColor: '#173149',
    borderWidth: 1,
    borderColor: '#2c587c',
    gap: 8,
  },
  kicker: {
    color: '#ffd24a',
    fontSize: 10,
    fontWeight: '900' as const,
    letterSpacing: 0,
  },
  title: {
    color: '#f7fbff',
    fontSize: 24,
    fontWeight: '900' as const,
  },
  refreshButton: {
    alignSelf: 'flex-start' as const,
    borderRadius: 8,
    backgroundColor: '#e85d4f',
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  refreshText: {
    color: '#ffffff',
    fontWeight: '900' as const,
  },
  empty: {
    borderRadius: 8,
    padding: 16,
    backgroundColor: '#ffffff',
    borderWidth: 1,
    borderColor: '#b8cad1',
  },
  emptyTitle: {
    color: '#102233',
    fontSize: 18,
    fontWeight: '900' as const,
  },
  emptyText: {
    color: '#4f6678',
    marginTop: 6,
    fontWeight: '700' as const,
  },
  reviewCard: {
    borderRadius: 8,
    padding: 12,
    backgroundColor: '#ffffff',
    borderWidth: 1,
    borderColor: '#b8cad1',
    gap: 10,
  },
  photo: {
    width: '100%' as const,
    aspectRatio: 1.2,
    borderRadius: 8,
    backgroundColor: '#d8e5dd',
  },
  time: {
    color: '#4f6678',
    fontSize: 12,
    fontWeight: '800' as const,
  },
  candidateGrid: {
    gap: 8,
  },
  candidateCard: {
    borderRadius: 8,
    padding: 12,
    backgroundColor: '#fff8d9',
    borderWidth: 2,
    borderColor: '#ffd24a',
  },
  candidateName: {
    color: '#102233',
    fontSize: 18,
    fontWeight: '900' as const,
  },
  candidateKind: {
    color: '#e85d4f',
    fontSize: 12,
    marginTop: 2,
    fontWeight: '900' as const,
  },
  reason: {
    color: '#4f6678',
    lineHeight: 18,
    marginTop: 8,
    fontWeight: '700' as const,
  },
  pick: {
    alignSelf: 'flex-start' as const,
    overflow: 'hidden' as const,
    borderRadius: 8,
    marginTop: 10,
    paddingHorizontal: 10,
    paddingVertical: 6,
    backgroundColor: '#2f9d75',
    color: '#ffffff',
    fontWeight: '900' as const,
  },
};
