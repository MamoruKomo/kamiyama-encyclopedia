import { Image, Pressable, ScrollView, Text, View } from 'react-native';

import { speciesCandidates } from '../data/kamiyama';
import type { Observation, Rarity } from '../types/domain';

type EncyclopediaPanelProps = {
  observations: Observation[];
  onDeleteObservation: (id: string) => void;
};

const rarityLabel: Record<Rarity, string> = {
  common: 'COMMON',
  uncommon: 'UNCOMMON',
  rare: 'RARE',
  special: 'SPECIAL',
};

const rarityColor: Record<Rarity, string> = {
  common: '#3f8f65',
  uncommon: '#2d83c4',
  rare: '#9f55bd',
  special: '#df604a',
};

export function EncyclopediaPanel({
  observations,
  onDeleteObservation,
}: EncyclopediaPanelProps) {
  const discoveredSpecies = new Set(observations.map((observation) => observation.candidateId).filter(Boolean));

  return (
    <ScrollView contentContainerStyle={{ paddingBottom: 120 }}>
      <View style={{ gap: 16 }}>
        <View style={styles.statsRow}>
          <Stat label="観察" value={observations.length.toString()} />
          <Stat label="候補種" value={`${discoveredSpecies.size}/${speciesCandidates.length}`} />
          <Stat label="レア以上" value={observations.filter((item) => item.rarity === 'rare' || item.rarity === 'special').length.toString()} />
        </View>

        {observations.length === 0 ? (
          <View style={styles.emptyBox}>
            <Text style={styles.emptyTitle}>まだ発見がありません</Text>
            <Text style={styles.emptyText}>
              撮影画面で植物や虫を撮ると、ここに写真・日時・場所が保存されます。
            </Text>
          </View>
        ) : null}

        {observations.map((observation) => {
          const candidate = speciesCandidates.find((item) => item.id === observation.candidateId);
          return (
            <View key={observation.id} style={styles.card}>
              <Image source={{ uri: observation.photoUri }} style={styles.cardImage} />
              <View style={styles.cardBody}>
                <View style={styles.cardHeader}>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.cardTitle}>{observation.customName}</Text>
                    <Text style={styles.cardSubTitle}>
                      {candidate?.scientificName ?? '未同定'} / {observation.category === 'plant' ? '植物' : '虫'}
                    </Text>
                  </View>
                  <Text
                    style={[
                      styles.rarity,
                      { backgroundColor: rarityColor[observation.rarity] },
                    ]}
                  >
                    {rarityLabel[observation.rarity]}
                  </Text>
                </View>
                <Text style={styles.meta}>
                  {new Date(observation.observedAt).toLocaleString('ja-JP')}
                </Text>
                <Text style={styles.meta}>
                  {observation.environment} / {observation.latitude.toFixed(5)}, {observation.longitude.toFixed(5)}
                </Text>
                {observation.note ? <Text style={styles.note}>{observation.note}</Text> : null}
                {candidate ? <Text style={styles.hint}>{candidate.hint}</Text> : null}
                <Pressable onPress={() => onDeleteObservation(observation.id)} style={styles.deleteButton}>
                  <Text style={styles.deleteText}>削除</Text>
                </Pressable>
              </View>
            </View>
          );
        })}
      </View>
    </ScrollView>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.stat}>
      <Text style={styles.statValue}>{value}</Text>
      <Text style={styles.statLabel}>{label}</Text>
    </View>
  );
}

const styles = {
  statsRow: {
    flexDirection: 'row' as const,
    gap: 8,
  },
  stat: {
    flex: 1,
    minHeight: 70,
    borderRadius: 8,
    justifyContent: 'center' as const,
    paddingHorizontal: 12,
    backgroundColor: '#f1f5ef',
    borderWidth: 1,
    borderColor: '#d7e0d8',
  },
  statValue: {
    color: '#14231a',
    fontSize: 20,
    fontWeight: '900' as const,
  },
  statLabel: {
    color: '#617068',
    marginTop: 2,
    fontSize: 12,
  },
  emptyBox: {
    borderRadius: 8,
    padding: 16,
    backgroundColor: '#ffffff',
    borderWidth: 1,
    borderColor: '#d7e0d8',
  },
  emptyTitle: {
    color: '#14231a',
    fontSize: 17,
    fontWeight: '900' as const,
  },
  emptyText: {
    color: '#59675e',
    lineHeight: 20,
    marginTop: 8,
  },
  card: {
    flexDirection: 'row' as const,
    gap: 12,
    borderRadius: 8,
    padding: 10,
    backgroundColor: '#ffffff',
    borderWidth: 1,
    borderColor: '#d7e0d8',
  },
  cardImage: {
    width: 108,
    height: 108,
    borderRadius: 8,
    backgroundColor: '#e5ebe6',
  },
  cardBody: {
    flex: 1,
    gap: 6,
  },
  cardHeader: {
    flexDirection: 'row' as const,
    gap: 8,
  },
  cardTitle: {
    color: '#14231a',
    fontSize: 16,
    fontWeight: '900' as const,
  },
  cardSubTitle: {
    color: '#617068',
    fontSize: 12,
    fontStyle: 'italic' as const,
    marginTop: 2,
  },
  rarity: {
    alignSelf: 'flex-start' as const,
    overflow: 'hidden' as const,
    borderRadius: 6,
    paddingHorizontal: 7,
    paddingVertical: 4,
    color: '#ffffff',
    fontSize: 10,
    fontWeight: '900' as const,
  },
  meta: {
    color: '#59675e',
    fontSize: 12,
    lineHeight: 17,
  },
  note: {
    color: '#26372d',
    lineHeight: 19,
  },
  hint: {
    color: '#59675e',
    fontSize: 12,
    lineHeight: 17,
  },
  deleteButton: {
    alignSelf: 'flex-start' as const,
    minHeight: 32,
    justifyContent: 'center' as const,
  },
  deleteText: {
    color: '#b44738',
    fontWeight: '800' as const,
  },
};
