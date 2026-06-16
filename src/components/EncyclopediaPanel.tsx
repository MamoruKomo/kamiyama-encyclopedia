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
  const rareCount = observations.filter((item) => item.rarity === 'rare' || item.rarity === 'special').length;
  const completionPercent = Math.round((discoveredSpecies.size / speciesCandidates.length) * 100);

  return (
    <ScrollView contentContainerStyle={{ paddingBottom: 120 }}>
      <View style={{ gap: 16 }}>
        <View style={styles.dexHero}>
          <Text style={styles.dexKicker}>BIO DEX</Text>
          <Text style={styles.dexTitle}>発見コレクション</Text>
          <Text style={styles.dexText}>
            写真と位置で神山町の自分だけの分布図を育てます。レアな記録ほどカードが強く光ります。
          </Text>
          <View style={styles.progressTrack}>
            <View style={[styles.progressFill, { width: `${completionPercent}%` }]} />
          </View>
        </View>

        <View style={styles.statsRow}>
          <Stat label="観察" value={observations.length.toString()} />
          <Stat label="候補種" value={`${discoveredSpecies.size}/${speciesCandidates.length}`} />
          <Stat label="レア以上" value={rareCount.toString()} accent />
        </View>

        {observations.length === 0 ? (
          <View style={styles.emptyBox}>
            <Text style={styles.emptyTitle}>最初の発見を待っています</Text>
            <Text style={styles.emptyText}>
              撮影画面で植物や虫を記録すると、写真・日時・場所・レア度がここに並びます。
            </Text>
          </View>
        ) : null}

        {observations.map((observation) => {
          const candidate = speciesCandidates.find((item) => item.id === observation.candidateId);
          return (
            <View
              key={observation.id}
              style={[
                styles.card,
                {
                  borderColor: rarityColor[observation.rarity],
                },
              ]}
            >
              <Image source={{ uri: observation.photoUri }} style={styles.cardImage} />
              <View style={styles.cardBody}>
                <View style={styles.cardHeader}>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.cardKicker}>
                      {observation.category === 'plant' ? 'PLANT' : 'INSECT'}
                      {observation.source === 'thinklet' ? ' / THINKLET' : ''}
                    </Text>
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

function Stat({ label, value, accent = false }: { label: string; value: string; accent?: boolean }) {
  return (
    <View style={[styles.stat, accent && styles.statAccent]}>
      <Text style={[styles.statValue, accent && styles.statValueAccent]}>{value}</Text>
      <Text style={styles.statLabel}>{label}</Text>
    </View>
  );
}

const styles = {
  dexHero: {
    borderRadius: 8,
    padding: 16,
    backgroundColor: '#102233',
    borderWidth: 1,
    borderColor: '#244763',
    gap: 5,
  },
  dexKicker: {
    color: '#68dcb0',
    fontSize: 10,
    fontWeight: '900' as const,
    letterSpacing: 0,
  },
  dexTitle: {
    color: '#f7fbff',
    fontSize: 21,
    fontWeight: '900' as const,
  },
  dexText: {
    color: '#b9ceda',
    fontSize: 13,
    lineHeight: 19,
    fontWeight: '600' as const,
  },
  progressTrack: {
    height: 8,
    borderRadius: 8,
    overflow: 'hidden' as const,
    backgroundColor: 'rgba(255,255,255,0.16)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.18)',
    marginTop: 8,
  },
  progressFill: {
    height: '100%' as const,
    borderRadius: 8,
    backgroundColor: '#ffd24a',
  },
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
    backgroundColor: '#ffffff',
    borderWidth: 1,
    borderColor: '#b8cad1',
  },
  statAccent: {
    backgroundColor: '#fff8d9',
    borderColor: '#ffd24a',
  },
  statValue: {
    color: '#102233',
    fontSize: 20,
    fontWeight: '900' as const,
  },
  statValueAccent: {
    color: '#e85d4f',
  },
  statLabel: {
    color: '#6b7f8f',
    marginTop: 2,
    fontSize: 12,
    fontWeight: '800' as const,
  },
  emptyBox: {
    borderRadius: 8,
    padding: 16,
    backgroundColor: '#ffffff',
    borderWidth: 1,
    borderColor: '#b8cad1',
  },
  emptyTitle: {
    color: '#102233',
    fontSize: 17,
    fontWeight: '900' as const,
  },
  emptyText: {
    color: '#4f6678',
    lineHeight: 20,
    marginTop: 8,
    fontWeight: '600' as const,
  },
  card: {
    flexDirection: 'row' as const,
    gap: 12,
    borderRadius: 8,
    padding: 10,
    backgroundColor: '#ffffff',
    borderWidth: 2,
    shadowColor: '#102233',
    shadowOpacity: 0.09,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 6 },
  },
  cardImage: {
    width: 108,
    height: 108,
    borderRadius: 8,
    backgroundColor: '#dce8ea',
  },
  cardBody: {
    flex: 1,
    gap: 6,
  },
  cardHeader: {
    flexDirection: 'row' as const,
    gap: 8,
  },
  cardKicker: {
    color: '#2b86c5',
    fontSize: 10,
    fontWeight: '900' as const,
    marginBottom: 2,
  },
  cardTitle: {
    color: '#102233',
    fontSize: 16,
    fontWeight: '900' as const,
  },
  cardSubTitle: {
    color: '#6b7f8f',
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
    color: '#4f6678',
    fontSize: 12,
    lineHeight: 17,
    fontWeight: '700' as const,
  },
  note: {
    color: '#26394b',
    lineHeight: 19,
  },
  hint: {
    color: '#4f6678',
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
