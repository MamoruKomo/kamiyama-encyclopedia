export interface Env {
  OBSERVATIONS: KVNamespace;
  OBSERVATION_DB?: D1Database;
  OBSERVATION_IMAGES?: R2Bucket;
  ALLOWED_ORIGINS?: string;
  SYNC_WRITE_TOKEN?: string;
  OPENAI_API_KEY?: string;
  OPENAI_MODEL?: string;
  AI_MODE?: string;
  MAX_UPLOAD_BYTES?: string;
}

type ThinkletObservationPayload = {
  id?: string;
  source?: string;
  category?: string;
  label?: string;
  confidence?: number | null;
  aiLabel?: string | null;
  aiConfidence?: number | null;
  aiAnalysis?: SpeciesAnalysis | null;
  latitude?: number | null;
  longitude?: number | null;
  accuracyMeters?: number | null;
  observedAt?: number | string;
  photoUri?: string | null;
  photoBase64?: string | null;
  photoMimeType?: string | null;
  photoDataUrl?: string | null;
  receivedAt?: number;
};

type SpeciesAnalysis = {
  category: 'plant' | 'insect' | 'unknown';
  commonName: string;
  scientificName?: string | null;
  rarity?: RarityValue | null;
  confidence: number;
  reason: string;
};

type RarityValue = 'common' | 'uncommon' | 'rare' | 'special';
type ObservationStatus =
  | 'uploaded'
  | 'classifying'
  | 'candidate_ready'
  | 'needs_review'
  | 'needs_retake'
  | 'confirmed'
  | 'rejected'
  | 'classification_failed';

type BroadCategory =
  | 'insect'
  | 'butterfly'
  | 'beetle'
  | 'plant'
  | 'flower'
  | 'tree'
  | 'mushroom'
  | 'unknown';

type CandidateProfile = {
  commonName: string;
  aliases: readonly string[];
  scientificName: string;
  rarity: RarityValue;
  hint: string;
  visualKeywords: readonly string[];
  seasonMonths: readonly number[];
  safety?: string;
};

type CandidateReferenceImage = {
  candidate: CandidateProfile;
  url: string;
  source: 'GBIF';
  license?: string;
};

type CandidateSpeciesResult = {
  species_id: string;
  reason: string;
};

type ClassificationInput = {
  observationId: string;
  clientObservationId: string;
  deviceId: string;
  capturedAt: string;
  latitude: number | null;
  longitude: number | null;
  mlLabels: MlLabel[];
  imageDataUrl?: string | null;
};

type ClassificationResult = {
  broad_category: BroadCategory;
  candidates: CandidateSpeciesResult[];
  requires_human_confirmation: boolean;
  needs_retake: boolean;
  classifier_mode: 'free' | 'openai';
  classifier_version: string;
};

type SpeciesClassifier = {
  classify(input: ClassificationInput, env: Env): Promise<ClassificationResult>;
};

type MlLabel = {
  text: string;
  confidence?: number | null;
};

type MultipartValue = string | File;

type ObservationRow = {
  id: string;
  client_observation_id: string;
  device_id: string;
  captured_at: string;
  received_at: string;
  latitude: number | null;
  longitude: number | null;
  public_latitude: number | null;
  public_longitude: number | null;
  location_accuracy_m: number | null;
  location_visibility: string;
  image_key: string;
  image_sha256: string;
  ml_labels_json: string;
  broad_category: string | null;
  candidate_species_json: string | null;
  confirmed_species_id: string | null;
  status: ObservationStatus;
  classifier_mode: string | null;
  classifier_version: string | null;
  quality_score: number | null;
  created_at: string;
  updated_at: string;
};

const DEFAULT_OPENAI_MODEL = 'gpt-5.4-mini';
const DEFAULT_AI_MODE = 'free';
const REFERENCE_IMAGE_CACHE_TTL_SECONDS = 60 * 60 * 24 * 14;
const MAX_REFERENCE_IMAGES_PER_CATEGORY = 8;
const DEFAULT_MAX_UPLOAD_BYTES = 4 * 1024 * 1024;
const SPECIES_ANALYSIS_SCHEMA = {
  type: 'object',
  required: ['category', 'commonName', 'confidence', 'reason'],
  properties: {
    category: { enum: ['plant', 'insect', 'unknown'] },
    commonName: { type: 'string' },
    scientificName: { type: ['string', 'null'] },
    rarity: { enum: ['common', 'uncommon', 'rare', 'special', null] },
    confidence: { type: 'number', minimum: 0, maximum: 1 },
    reason: { type: 'string' },
  },
} as const;

const PLANT_CANDIDATES = [
  {
    commonName: 'ヤブソテツ',
    aliases: ['ヤブソテツ類', 'シダ', 'つやのあるシダ'],
    scientificName: 'Cyrtomium fortunei',
    rarity: 'common',
    hint: '林縁や石垣にある、つやのあるシダ。葉のまとまりが見えると判断しやすい。',
    visualKeywords: ['羽状に並ぶ厚めの小葉', '濃い緑で光沢のある葉', '花や実は目立たない', '石垣や林縁に群生'],
    seasonMonths: [1, 2, 3, 4, 5, 10, 11, 12],
  },
  {
    commonName: 'ヒメウズ',
    aliases: ['姫烏頭', '小さな白い花'],
    scientificName: 'Semiaquilegia adoxoides',
    rarity: 'uncommon',
    hint: '春に小さな花をつける草。道端や林縁に低く咲く。',
    visualKeywords: ['細い茎', '小さな白から淡桃色の花', '丸みのある小葉', '背が低い草姿'],
    seasonMonths: [2, 3, 4, 5],
  },
  {
    commonName: 'マンリョウ',
    aliases: ['万両', '赤い実の低木'],
    scientificName: 'Ardisia crenata',
    rarity: 'common',
    hint: '赤い実と光沢のある葉が目印。林の下や半日陰に多い。',
    visualKeywords: ['赤い丸い実が房状につく', '濃緑で光沢のある楕円形の葉', '葉の縁が波打つ', '低い常緑低木'],
    seasonMonths: [1, 2, 11, 12],
  },
  {
    commonName: 'シュロ',
    aliases: ['棕櫚', 'ワジュロ', 'ヤシのような木'],
    scientificName: 'Trachycarpus fortunei',
    rarity: 'common',
    hint: '扇形の大きな葉が特徴。庭木や林縁で見つかりやすい。',
    visualKeywords: ['扇形に大きく広がる葉', '幹に茶色い繊維が残る', 'ヤシに似た姿', '庭木や集落周辺'],
    seasonMonths: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
  },
  {
    commonName: 'ジュズダマ',
    aliases: ['数珠玉', '硬い丸い実'],
    scientificName: 'Coix lacryma-jobi',
    rarity: 'rare',
    hint: '水辺や湿った草地に出る。硬い丸い実が連なる。',
    visualKeywords: ['硬く丸い灰色から黒っぽい実', 'トウモロコシに似た細長い葉', '水辺や湿地の草むら', '実が数珠のように見える'],
    seasonMonths: [8, 9, 10, 11],
  },
] as const satisfies ReadonlyArray<CandidateProfile>;

const TOKUSHIMA_PLANT_CANDIDATES = [
  {
    commonName: 'スギ',
    aliases: ['杉', 'まっすぐな針葉樹'],
    scientificName: 'Cryptomeria japonica',
    rarity: 'common',
    hint: '人工林や山ぎわに多い針葉樹。まっすぐな幹と細い針葉を撮る。',
    visualKeywords: ['まっすぐ高い幹', '赤褐色の樹皮', '細い針葉', '人工林にまとまって生える'],
    seasonMonths: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
  },
  {
    commonName: 'ヒノキ',
    aliases: ['檜', '扁平な葉の針葉樹'],
    scientificName: 'Chamaecyparis obtusa',
    rarity: 'common',
    hint: '山の人工林に多い。平たい鱗片状の葉と赤褐色の樹皮が目印。',
    visualKeywords: ['平たく広がる鱗片状の葉', '赤褐色の樹皮', '枝先が扇のように広がる', '人工林'],
    seasonMonths: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
  },
  {
    commonName: 'コナラ',
    aliases: ['小楢', 'どんぐりの木'],
    scientificName: 'Quercus serrata',
    rarity: 'common',
    hint: '里山の代表的な落葉樹。葉のぎざぎざとどんぐりを撮ると分かりやすい。',
    visualKeywords: ['細長い葉に粗い鋸歯', 'どんぐり', '落葉広葉樹', '里山の林'],
    seasonMonths: [4, 5, 6, 7, 8, 9, 10, 11],
  },
  {
    commonName: 'クヌギ',
    aliases: ['櫟', '樹液の木', 'カブトムシが来る木'],
    scientificName: 'Quercus acutissima',
    rarity: 'uncommon',
    hint: '樹液に虫が集まりやすい木。長い鋸歯の葉と丸いどんぐりを見る。',
    visualKeywords: ['細長い葉', '針のように伸びた鋸歯', '丸いどんぐり', '樹液が出る幹'],
    seasonMonths: [4, 5, 6, 7, 8, 9, 10, 11],
  },
  {
    commonName: 'アラカシ',
    aliases: ['粗樫', '常緑のどんぐり'],
    scientificName: 'Quercus glauca',
    rarity: 'common',
    hint: '神社や里山に多い常緑樹。厚めの葉とどんぐりを撮る。',
    visualKeywords: ['厚く光沢のある常緑葉', '葉の上半分に鋸歯', 'どんぐり', '暗い緑の樹冠'],
    seasonMonths: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
  },
  {
    commonName: 'ヤマザクラ',
    aliases: ['山桜', '野生の桜'],
    scientificName: 'Prunus jamasakura',
    rarity: 'uncommon',
    hint: '山ぎわに咲く桜。若葉と花が同時に出る様子を撮る。',
    visualKeywords: ['淡い桃色から白い花', '花と赤っぽい若葉が同時に出る', '山地の斜面', '桜の樹皮'],
    seasonMonths: [3, 4],
  },
  {
    commonName: 'イロハモミジ',
    aliases: ['紅葉', 'モミジ', 'カエデ'],
    scientificName: 'Acer palmatum',
    rarity: 'common',
    hint: '谷沿いや庭で見つかる。手のひら形の葉を正面から撮る。',
    visualKeywords: ['手のひら形の葉', '5から7つに深く裂ける', '秋に赤く紅葉', '細い枝'],
    seasonMonths: [4, 5, 6, 7, 8, 9, 10, 11],
  },
  {
    commonName: 'アカメガシワ',
    aliases: ['赤芽柏', '赤い新芽の木'],
    scientificName: 'Mallotus japonicus',
    rarity: 'common',
    hint: '空き地や林縁にすぐ出る木。赤みのある新芽と大きな葉が目印。',
    visualKeywords: ['大きな広い葉', '赤みのある新芽', '葉柄が長い', '明るい林縁や空き地'],
    seasonMonths: [4, 5, 6, 7, 8, 9, 10],
  },
  {
    commonName: 'クサギ',
    aliases: ['臭木', '白い花と青い実'],
    scientificName: 'Clerodendrum trichotomum',
    rarity: 'common',
    hint: '林縁に多い低木。夏の白い花、秋の青い実と赤いがくが特徴。',
    visualKeywords: ['大きな対生葉', '白い星形の花', '青い実と赤いがく', '枝葉に独特のにおい'],
    seasonMonths: [6, 7, 8, 9, 10],
  },
  {
    commonName: 'ネムノキ',
    aliases: ['合歓木', 'ふわふわのピンク花'],
    scientificName: 'Albizia julibrissin',
    rarity: 'uncommon',
    hint: '川沿いや道沿いに出る木。羽のような葉とピンクの花を撮る。',
    visualKeywords: ['細かい羽状複葉', '淡桃色のふわふわした花', '夜に葉が閉じる', '横に広がる枝'],
    seasonMonths: [6, 7, 8],
  },
  {
    commonName: 'クスノキ',
    aliases: ['楠', 'くすのき'],
    scientificName: 'Cinnamomum camphora',
    rarity: 'common',
    hint: '神社や集落に多い常緑樹。三本の葉脈と光沢のある葉を見る。',
    visualKeywords: ['光沢のある楕円形の葉', '葉の付け根から三本の脈', '大木になりやすい', '常緑樹'],
    seasonMonths: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
  },
  {
    commonName: 'ヤブツバキ',
    aliases: ['椿', 'ツバキ'],
    scientificName: 'Camellia japonica',
    rarity: 'common',
    hint: '冬から春の赤い花が目立つ。厚く光沢のある葉も撮る。',
    visualKeywords: ['赤い大きな花', '厚く光沢のある葉', '花ごと落ちる', '常緑低木から小高木'],
    seasonMonths: [1, 2, 3, 4, 12],
  },
  {
    commonName: 'サザンカ',
    aliases: ['山茶花'],
    scientificName: 'Camellia sasanqua',
    rarity: 'common',
    hint: '庭木や生け垣で見やすい。花びらが一枚ずつ散るツバキの仲間。',
    visualKeywords: ['秋から冬の花', '白から桃色の花', '花びらが一枚ずつ散る', '細かい鋸歯の葉'],
    seasonMonths: [10, 11, 12, 1, 2],
  },
  {
    commonName: 'アオキ',
    aliases: ['青木', '赤い実の常緑低木'],
    scientificName: 'Aucuba japonica',
    rarity: 'common',
    hint: '林の下や庭に多い。大きな光沢葉と赤い実を見る。',
    visualKeywords: ['大きく光沢のある葉', '葉に粗い鋸歯', '赤い楕円形の実', '日陰の低木'],
    seasonMonths: [1, 2, 3, 4, 11, 12],
  },
  {
    commonName: 'ナンテン',
    aliases: ['南天', '赤い実'],
    scientificName: 'Nandina domestica',
    rarity: 'common',
    hint: '庭や林縁に多い。細かく分かれた葉と赤い実を撮る。',
    visualKeywords: ['細かく分かれる複葉', '赤い丸い実の房', '細い茎が株立ち', '庭木や林縁'],
    seasonMonths: [1, 2, 6, 7, 11, 12],
  },
  {
    commonName: 'ヤツデ',
    aliases: ['八手', '大きな手の葉'],
    scientificName: 'Fatsia japonica',
    rarity: 'common',
    hint: '日陰の庭や林縁で見つかる。大きな手のひら形の葉が目印。',
    visualKeywords: ['大きな手のひら形の葉', '深く裂ける常緑葉', '白い丸い花序', '日陰に生える'],
    seasonMonths: [1, 2, 3, 10, 11, 12],
  },
  {
    commonName: 'ヤマアジサイ',
    aliases: ['山紫陽花', '小さなアジサイ'],
    scientificName: 'Hydrangea serrata',
    rarity: 'uncommon',
    hint: '沢沿いや山ぎわに咲く。中心の小花と周りの装飾花を撮る。',
    visualKeywords: ['小型のアジサイ', '周囲に装飾花', '鋸歯のある葉', '湿った林縁や沢沿い'],
    seasonMonths: [5, 6, 7],
  },
  {
    commonName: 'ドクダミ',
    aliases: ['十薬', '白い十字の花'],
    scientificName: 'Houttuynia cordata',
    rarity: 'common',
    hint: '湿った日陰に群生する草。白い苞とハート形の葉を撮る。',
    visualKeywords: ['白い十字形に見える苞', 'ハート形の葉', '湿った日陰に群生', '独特のにおい'],
    seasonMonths: [5, 6, 7],
  },
  {
    commonName: 'ヨモギ',
    aliases: ['蓬', 'もち草'],
    scientificName: 'Artemisia indica',
    rarity: 'common',
    hint: '道端や畑の縁に多い。葉の裏が白っぽいところを撮る。',
    visualKeywords: ['深く切れ込む葉', '葉裏が白っぽい', '道端や草地', '細かい花序'],
    seasonMonths: [3, 4, 5, 6, 7, 8, 9, 10],
  },
  {
    commonName: 'ススキ',
    aliases: ['芒', '尾花'],
    scientificName: 'Miscanthus sinensis',
    rarity: 'common',
    hint: '秋の草地で目立つ大型草本。銀色の穂を入れて撮る。',
    visualKeywords: ['細長い葉', '銀色から白い穂', '大きな株になる', '秋の草地や斜面'],
    seasonMonths: [8, 9, 10, 11],
  },
  {
    commonName: 'チガヤ',
    aliases: ['茅', '白い穂の草'],
    scientificName: 'Imperata cylindrica',
    rarity: 'common',
    hint: '草地や畑の縁に多い。白い綿毛のような穂を撮る。',
    visualKeywords: ['細い葉', '白くふわふわした穂', '草地に群生', '地下茎で広がる'],
    seasonMonths: [4, 5, 6],
  },
  {
    commonName: 'セイタカアワダチソウ',
    aliases: ['背高泡立草', '黄色い外来草'],
    scientificName: 'Solidago altissima',
    rarity: 'common',
    hint: '秋に黄色い花をたくさんつける背の高い草。河原や空き地で見つかる。',
    visualKeywords: ['背が高い草', '黄色い小花が密につく', '細長い葉', '空き地や河原に群生'],
    seasonMonths: [9, 10, 11],
  },
  {
    commonName: 'クズ',
    aliases: ['葛', '大きな三枚葉のつる'],
    scientificName: 'Pueraria montana var. lobata',
    rarity: 'common',
    hint: '斜面や道沿いを覆う大型つる植物。三枚葉と紫の花を見る。',
    visualKeywords: ['大きな三出複葉', 'つるで広く覆う', '紫色の花房', '斜面や林縁'],
    seasonMonths: [5, 6, 7, 8, 9],
  },
  {
    commonName: 'フジ',
    aliases: ['藤', '紫の花房'],
    scientificName: 'Wisteria floribunda',
    rarity: 'uncommon',
    hint: '春に紫の花房が垂れるつる植物。林縁や棚で見つかる。',
    visualKeywords: ['垂れ下がる紫の花房', '羽状複葉', '太いつる', '春に咲く'],
    seasonMonths: [4, 5],
  },
  {
    commonName: 'アケビ',
    aliases: ['木通', '五枚葉のつる'],
    scientificName: 'Akebia quinata',
    rarity: 'uncommon',
    hint: '山ぎわのつる植物。五枚の小葉と紫色の花や実を撮る。',
    visualKeywords: ['五枚の小葉', '紫色の花', '楕円形の実が割れる', 'つる植物'],
    seasonMonths: [3, 4, 9, 10],
  },
  {
    commonName: 'ヤマノイモ',
    aliases: ['山芋', 'むかご'],
    scientificName: 'Dioscorea japonica',
    rarity: 'common',
    hint: '林縁のつる植物。ハート形の葉とむかごを探す。',
    visualKeywords: ['細長いハート形の葉', 'つる植物', '葉の付け根にむかご', '林縁に絡む'],
    seasonMonths: [6, 7, 8, 9, 10],
  },
  {
    commonName: 'ツユクサ',
    aliases: ['露草', '青い花'],
    scientificName: 'Commelina communis',
    rarity: 'common',
    hint: '道端や畑の縁の青い花。花の形を正面から撮る。',
    visualKeywords: ['鮮やかな青い二枚の花弁', '細長い葉', '地面近くに広がる', '朝に咲く'],
    seasonMonths: [6, 7, 8, 9, 10],
  },
  {
    commonName: 'ヒガンバナ',
    aliases: ['彼岸花', '曼珠沙華'],
    scientificName: 'Lycoris radiata',
    rarity: 'common',
    hint: '秋の田んぼや道端で赤く咲く。花と葉が同時にないことが多い。',
    visualKeywords: ['赤い放射状の花', '細長い雄しべ', '秋の畦や道端', '花の時期に葉が目立たない'],
    seasonMonths: [9, 10],
  },
  {
    commonName: 'シロツメクサ',
    aliases: ['白詰草', 'クローバー'],
    scientificName: 'Trifolium repens',
    rarity: 'common',
    hint: '芝生や草地のクローバー。三枚葉と白い丸い花を撮る。',
    visualKeywords: ['三枚の小葉', '白い丸い花序', '地面をはう', '芝生や草地'],
    seasonMonths: [4, 5, 6, 7, 8, 9, 10],
  },
  {
    commonName: 'オオバコ',
    aliases: ['大葉子'],
    scientificName: 'Plantago asiatica',
    rarity: 'common',
    hint: '踏まれる道端に強い草。根元から広がる葉と細い穂を見る。',
    visualKeywords: ['根元から広がる葉', '平行に走る葉脈', '細長い花穂', '道端や踏み跡'],
    seasonMonths: [4, 5, 6, 7, 8, 9],
  },
  {
    commonName: 'カタバミ',
    aliases: ['片喰', '小さな黄色い花'],
    scientificName: 'Oxalis corniculata',
    rarity: 'common',
    hint: '庭や道端に出る小さな草。ハート形の三枚葉と黄色い花を撮る。',
    visualKeywords: ['ハート形の三枚葉', '小さな黄色い五弁花', '地面をはう', '細い実'],
    seasonMonths: [3, 4, 5, 6, 7, 8, 9, 10, 11],
  },
  {
    commonName: 'ホトケノザ',
    aliases: ['仏の座', '春の紫花'],
    scientificName: 'Lamium amplexicaule',
    rarity: 'common',
    hint: '春の畑や道端に多い。段々につく丸い葉と紫の花を見る。',
    visualKeywords: ['紫色の唇形花', '茎を囲む丸い葉', '春の畑や道端', '背が低い草'],
    seasonMonths: [2, 3, 4, 5],
  },
  {
    commonName: 'オオイヌノフグリ',
    aliases: ['青い小花'],
    scientificName: 'Veronica persica',
    rarity: 'common',
    hint: '早春の道端に咲く小さな青い花。花を近くで撮る。',
    visualKeywords: ['小さな青い四弁花', '白い中心', '丸い鋸歯の葉', '春の草地'],
    seasonMonths: [2, 3, 4, 5],
  },
  {
    commonName: 'ナズナ',
    aliases: ['ぺんぺん草', '薺'],
    scientificName: 'Capsella bursa-pastoris',
    rarity: 'common',
    hint: '春の道端に多い。ハート形の実がつく茎を撮る。',
    visualKeywords: ['小さな白い花', '三角からハート形の実', '細い花茎', 'ロゼット葉'],
    seasonMonths: [2, 3, 4, 5, 6],
  },
  {
    commonName: 'レンゲ',
    aliases: ['ゲンゲ', '蓮華草'],
    scientificName: 'Astragalus sinicus',
    rarity: 'uncommon',
    hint: '春の田畑に咲くピンクの花。花の房と羽状の葉を撮る。',
    visualKeywords: ['淡紫から桃色の花が輪状につく', '羽状複葉', '田畑や草地', '春に群生'],
    seasonMonths: [3, 4, 5],
  },
  {
    commonName: 'ゲンノショウコ',
    aliases: ['現の証拠', '小さな五弁花'],
    scientificName: 'Geranium thunbergii',
    rarity: 'common',
    hint: '道端や草地に出る。小さな五弁花と掌状に裂けた葉を見る。',
    visualKeywords: ['白から淡紅色の五弁花', '掌状に裂ける葉', '細い実が伸びる', '草地や林縁'],
    seasonMonths: [7, 8, 9, 10],
  },
  {
    commonName: 'ヘクソカズラ',
    aliases: ['屁糞葛', 'ヤイトバナ'],
    scientificName: 'Paederia foetida',
    rarity: 'common',
    hint: 'フェンスや草に絡むつる。白い筒形花と赤い中心を撮る。',
    visualKeywords: ['つる植物', '白い筒形花', '花の中心が赤紫', '対生の葉'],
    seasonMonths: [7, 8, 9],
  },
  {
    commonName: 'イタドリ',
    aliases: ['虎杖', 'すかんぽ'],
    scientificName: 'Reynoutria japonica',
    rarity: 'common',
    hint: '川沿いや斜面に多い大型草本。節のある茎と広い葉を見る。',
    visualKeywords: ['太く節のある茎', '広い三角形の葉', '白い小花の房', '河原や斜面に群生'],
    seasonMonths: [4, 5, 6, 7, 8, 9, 10],
  },
  {
    commonName: 'ミズヒキ',
    aliases: ['水引', '赤白の細い花穂'],
    scientificName: 'Persicaria filiformis',
    rarity: 'common',
    hint: '林縁や庭の日陰に出る。細い赤い花穂を横から撮る。',
    visualKeywords: ['細長い赤い花穂', '楕円形の葉', '節のある茎', '日陰の草地'],
    seasonMonths: [7, 8, 9, 10],
  },
  {
    commonName: 'ワラビ',
    aliases: ['蕨', '山菜のシダ'],
    scientificName: 'Pteridium aquilinum',
    rarity: 'common',
    hint: '明るい斜面や草地に出るシダ。三角形に広がる葉を撮る。',
    visualKeywords: ['大きく三角形に広がる葉', '羽状に細かく裂ける', '春の若芽は巻く', '明るい斜面'],
    seasonMonths: [3, 4, 5, 6, 7, 8, 9],
  },
  {
    commonName: 'ゼンマイ',
    aliases: ['薇', '渦巻きの若芽'],
    scientificName: 'Osmunda japonica',
    rarity: 'uncommon',
    hint: '湿った山ぎわに出るシダ。春の渦巻き状の若芽が目印。',
    visualKeywords: ['渦巻き状の若芽', '羽状に広がるシダ葉', '湿った林縁', '胞子葉が立つ'],
    seasonMonths: [3, 4, 5, 6, 7, 8],
  },
  {
    commonName: 'スギナ',
    aliases: ['杉菜', 'つくし'],
    scientificName: 'Equisetum arvense',
    rarity: 'common',
    hint: '春につくし、後から細い緑のスギナが出る。畑や道端で探す。',
    visualKeywords: ['節のある細い茎', '輪生する細い枝', '春につくしが出る', '畑や道端'],
    seasonMonths: [3, 4, 5, 6, 7, 8, 9],
  },
  {
    commonName: 'トリアシショウマ',
    aliases: ['鳥足升麻', '白い穂の山野草'],
    scientificName: 'Astilbe thunbergii',
    rarity: 'uncommon',
    hint: '徳島県内のGBIF記録で多い山野草。白い細かな花穂と複葉を見る。',
    visualKeywords: ['白い細かな花が穂状につく', '細かく分かれる複葉', '湿った林縁', '山地の草本'],
    seasonMonths: [6, 7, 8],
  },
] as const satisfies ReadonlyArray<CandidateProfile>;

const AI_PLANT_CANDIDATES = [
  ...PLANT_CANDIDATES,
  ...TOKUSHIMA_PLANT_CANDIDATES,
] as const satisfies ReadonlyArray<CandidateProfile>;

const INSECT_CANDIDATES = [
  {
    commonName: 'キイロスズメバチ',
    aliases: ['黄色いスズメバチ', 'スズメバチ'],
    scientificName: 'Vespa simillima xanthoptera',
    rarity: 'uncommon',
    hint: '黄色と黒の大型ハチ。安全上、巣や個体に近づきすぎない。',
    visualKeywords: ['黄色と黒のしま模様', '大型で腰がくびれたハチ', '顔や胸に黄色が目立つ', '巣や樹液周辺にいることがある'],
    seasonMonths: [5, 6, 7, 8, 9, 10, 11],
    safety: '危険なハチなので近づかず先生に知らせる。',
  },
  {
    commonName: 'ニホンミツバチ',
    aliases: ['日本蜜蜂', 'ミツバチ'],
    scientificName: 'Apis cerana',
    rarity: 'rare',
    hint: '花に来る小型のミツバチ。全体に黒っぽく、腹部の縞が見えることがある。',
    visualKeywords: ['小型のハチ', '全体に黒っぽい', '腹部に細い縞', '花に止まって蜜を吸う'],
    seasonMonths: [3, 4, 5, 6, 7, 8, 9, 10],
  },
  {
    commonName: 'カブトムシ',
    aliases: ['甲虫', 'カブト', '角のある虫', 'rhinoceros beetle'],
    scientificName: 'Trypoxylus dichotomus',
    rarity: 'rare',
    hint: '夏の夜、クヌギやコナラの樹液に集まる大型の甲虫。オスは長い角が目印。',
    visualKeywords: ['大きな黒褐色の甲虫', 'オスは頭に長い角', '丸く光沢のある体', '樹液や夜の灯りに来る'],
    seasonMonths: [6, 7, 8],
  },
  {
    commonName: 'ノコギリクワガタ',
    aliases: ['クワガタ', '鋸鍬形', 'stag beetle'],
    scientificName: 'Prosopocoilus inclinatus',
    rarity: 'rare',
    hint: '夏に樹液へ集まるクワガタ。オスはのこぎりのような大あごが特徴。',
    visualKeywords: ['黒から赤褐色の甲虫', 'オスは湾曲した大あご', '大あごに小さな歯が並ぶ', '樹液に集まる'],
    seasonMonths: [6, 7, 8],
  },
  {
    commonName: 'テングチョウ',
    aliases: ['天狗蝶', '鼻が長い蝶'],
    scientificName: 'Libythea lepita',
    rarity: 'uncommon',
    hint: '顔先が突き出て見える蝶。翅は褐色から橙色の模様。',
    visualKeywords: ['顔先が天狗の鼻のように突き出る', '褐色の翅に橙色の斑', '翅の外側が角ばる', '枯れ葉に似て見える'],
    seasonMonths: [3, 4, 5, 6, 10, 11],
  },
  {
    commonName: 'ベニシジミ',
    aliases: ['紅小灰蝶', '小さいオレンジの蝶'],
    scientificName: 'Lycaena phlaeas daimio',
    rarity: 'common',
    hint: '小型で橙色が目立つ蝶。草地や畑の縁に多い。',
    visualKeywords: ['小型の蝶', '前翅が鮮やかな橙色', '黒い斑点', '草地で低く飛ぶ'],
    seasonMonths: [3, 4, 5, 6, 7, 8, 9, 10, 11],
  },
  {
    commonName: 'ツマグロヒョウモン',
    aliases: ['褄黒豹紋', 'オレンジのヒョウ柄蝶'],
    scientificName: 'Argynnis hyperbius',
    rarity: 'common',
    hint: '橙色のヒョウ柄模様を持つ蝶。花の多い場所で見つかりやすい。',
    visualKeywords: ['中型から大型の蝶', '橙色に黒いヒョウ柄', 'メスは翅の先が黒っぽい', '花に止まることが多い'],
    seasonMonths: [5, 6, 7, 8, 9, 10, 11],
  },
] as const satisfies ReadonlyArray<CandidateProfile>;

const JSON_HEADERS = {
  'content-type': 'application/json; charset=utf-8',
};

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);
    const corsHeaders = buildCorsHeaders(request, env);

    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: corsHeaders });
    }

    try {
      if (url.pathname === '/health') {
        return json({
          ok: true,
          aiMode: getAiMode(env),
          openaiConfigured: Boolean(env.OPENAI_API_KEY),
          openaiEnabled: shouldUseOpenAi(env),
          d1Configured: Boolean(env.OBSERVATION_DB),
          r2Configured: Boolean(env.OBSERVATION_IMAGES),
        }, corsHeaders);
      }

      if (url.pathname === '/api/v1/observations' && request.method === 'POST') {
        return await createObservationV1(request, env, ctx, corsHeaders);
      }

      if (url.pathname === '/api/v1/observations' && request.method === 'GET') {
        return await listObservationsV1(url, env, corsHeaders);
      }

      const imageMatch = url.pathname.match(/^\/api\/v1\/observations\/([^/]+)\/image$/);
      if (imageMatch && request.method === 'GET') {
        return await getObservationImage(imageMatch[1], env, corsHeaders);
      }

      const observationMatch = url.pathname.match(/^\/api\/v1\/observations\/([^/]+)$/);
      if (observationMatch && request.method === 'PATCH') {
        return await updateObservationReview(observationMatch[1], request, env, corsHeaders);
      }

      if (url.pathname === '/observations' && request.method === 'POST') {
        return await createObservation(request, env, corsHeaders);
      }

      if (url.pathname === '/observations' && request.method === 'GET') {
        return await listObservations(url, env, corsHeaders);
      }

      return json({ error: 'not_found' }, corsHeaders, 404);
    } catch (error) {
      console.error(JSON.stringify({ message: 'request_failed', error: String(error) }));
      return json({ error: 'internal_error' }, corsHeaders, 500);
    }
  },
};

async function createObservation(
  request: Request,
  env: Env,
  corsHeaders: HeadersInit,
): Promise<Response> {
  if (!(await isAuthorized(request, env))) {
    return json({ error: 'unauthorized' }, corsHeaders, 401);
  }

  const payload = await request.json<ThinkletObservationPayload>();
  const now = Date.now();
  const id = sanitizeId(payload.id) ?? `thinklet-${now}-${crypto.randomUUID()}`;
  const photoDataUrl = buildPhotoDataUrl(payload);
  const aiAnalysis = await analyzeSpeciesPhoto(payload, photoDataUrl, env)
    ?? inferSpeciesFromDeviceSignal(payload);
  const normalized: ThinkletObservationPayload = {
    ...payload,
    id,
    source: 'THINKLET',
    category: normalizeCategory(aiAnalysis?.category ?? payload.category),
    label: aiAnalysis?.commonName ?? payload.label ?? 'Thinklet観察',
    confidence: aiAnalysis?.confidence ?? payload.confidence ?? null,
    aiLabel: aiAnalysis?.commonName ?? null,
    aiConfidence: aiAnalysis?.confidence ?? null,
    aiAnalysis,
    photoBase64: undefined,
    photoMimeType: undefined,
    photoDataUrl,
    receivedAt: now,
  };

  await env.OBSERVATIONS.put(`obs:${id}`, JSON.stringify(normalized), {
    metadata: {
      receivedAt: now,
      observedAt: normalizeObservedAt(payload.observedAt),
    },
  });

  return json({ ok: true, id, observation: normalized }, corsHeaders, 201);
}

async function createObservationV1(
  request: Request,
  env: Env,
  ctx: ExecutionContext,
  corsHeaders: HeadersInit,
): Promise<Response> {
  if (!(await isAuthorized(request, env))) {
    return json({ error: 'unauthorized' }, corsHeaders, 401);
  }
  if (!env.OBSERVATION_DB) {
    return json({
      error: 'storage_not_configured',
      message: 'D1 binding is required for /api/v1/observations.',
    }, corsHeaders, 503);
  }

  const form = await request.formData();
  const image = form.get('image');
  if (!isUploadedFile(image)) {
    return json({ error: 'image_required' }, corsHeaders, 400);
  }

  const deviceId = sanitizeRequiredId(form.get('device_id'), 'thinklet');
  const clientObservationId = sanitizeRequiredId(
    form.get('client_observation_id'),
    crypto.randomUUID(),
  );
  const duplicate = await findObservationByClient(env.OBSERVATION_DB, deviceId, clientObservationId);
  if (duplicate) {
    return json({
      observation_id: duplicate.id,
      client_observation_id: duplicate.client_observation_id,
      status: duplicate.status,
      duplicate: true,
      accepted_at: duplicate.received_at,
    }, corsHeaders);
  }

  const maxBytes = parseUploadLimit(env);
  if (image.size <= 0) {
    return json({ error: 'empty_image' }, corsHeaders, 400);
  }
  if (image.size > maxBytes) {
    return json({ error: 'image_too_large', max_bytes: maxBytes }, corsHeaders, 413);
  }

  const imageBytes = new Uint8Array(await image.arrayBuffer());
  const imageType = detectImageType(imageBytes, image.type);
  if (!imageType) {
    return json({ error: 'unsupported_image_type' }, corsHeaders, 415);
  }

  const nowIso = new Date().toISOString();
  const observationId = crypto.randomUUID();
  const capturedAt = normalizeIsoDate(form.get('captured_at')) ?? nowIso;
  const latitude = normalizeLatitude(form.get('latitude'));
  const longitude = normalizeLongitude(form.get('longitude'));
  const publicPoint = buildPublicPoint(latitude, longitude);
  const accuracy = normalizeNumber(form.get('location_accuracy_m'));
  const qualityScore = normalizeNumber(form.get('quality_score'));
  const mlLabels = parseMlLabels(form.get('ml_labels_json'));
  const imageKey = `observations/${deviceId}/${clientObservationId}.${imageType.extension}`;
  const imageSha256 = await sha256Hex(imageBytes);

  await putObservationImage(env, imageKey, imageBytes, imageType.contentType, {
    observation_id: observationId,
    device_id: deviceId,
    client_observation_id: clientObservationId,
    sha256: imageSha256,
  });

  try {
    await insertObservation(env.OBSERVATION_DB, {
      id: observationId,
      client_observation_id: clientObservationId,
      device_id: deviceId,
      captured_at: capturedAt,
      received_at: nowIso,
      latitude,
      longitude,
      public_latitude: publicPoint.latitude,
      public_longitude: publicPoint.longitude,
      location_accuracy_m: accuracy,
      location_visibility: 'public_rounded',
      image_key: imageKey,
      image_sha256: imageSha256,
      ml_labels_json: JSON.stringify(mlLabels),
      broad_category: null,
      candidate_species_json: null,
      confirmed_species_id: null,
      status: 'uploaded',
      classifier_mode: null,
      classifier_version: null,
      quality_score: qualityScore,
      created_at: nowIso,
      updated_at: nowIso,
    });
  } catch (error) {
    await deleteObservationImage(env, imageKey);
    const existing = await findObservationByClient(env.OBSERVATION_DB, deviceId, clientObservationId);
    if (existing) {
      return json({
        observation_id: existing.id,
        client_observation_id: existing.client_observation_id,
        status: existing.status,
        duplicate: true,
        accepted_at: existing.received_at,
      }, corsHeaders);
    }
    throw error;
  }

  ctx.waitUntil(classifyPersistedObservation(env, {
    observationId,
    clientObservationId,
    deviceId,
    capturedAt,
    latitude,
    longitude,
    mlLabels,
    imageDataUrl: shouldUseOpenAi(env)
      ? buildDataUrlFromBytes(imageBytes, imageType.contentType)
      : null,
  }));

  return json({
    observation_id: observationId,
    client_observation_id: clientObservationId,
    status: 'uploaded',
    duplicate: false,
    accepted_at: nowIso,
  }, corsHeaders, 201);
}

async function listObservationsV1(
  url: URL,
  env: Env,
  corsHeaders: HeadersInit,
): Promise<Response> {
  if (!env.OBSERVATION_DB) {
    return json({ observations: [], serverTime: Date.now(), storage: 'not_configured' }, corsHeaders);
  }
  const since = Number(url.searchParams.get('since') ?? '0');
  const sinceIso = since > 0 ? new Date(since).toISOString() : '1970-01-01T00:00:00.000Z';
  const status = url.searchParams.get('status');
  const includeReview = url.searchParams.get('include_review') === 'true';
  const allowedStatuses = new Set<ObservationStatus>([
    'uploaded',
    'classifying',
    'candidate_ready',
    'needs_review',
    'needs_retake',
    'confirmed',
    'rejected',
    'classification_failed',
  ]);
  const filters = ['updated_at > ?'];
  const binds: Array<string | number> = [sinceIso];
  if (status && allowedStatuses.has(status as ObservationStatus)) {
    filters.push('status = ?');
    binds.push(status);
  } else if (!includeReview) {
    filters.push("status = 'confirmed'");
  }

  const result = await env.OBSERVATION_DB.prepare(
    `SELECT * FROM observations WHERE ${filters.join(' AND ')} ORDER BY updated_at ASC LIMIT 500`,
  ).bind(...binds).all<ObservationRow>();
  const observations = (result.results ?? []).map(rowToApiObservation);
  return json({ observations, serverTime: Date.now(), storage: 'd1' }, corsHeaders);
}

async function getObservationImage(
  observationId: string,
  env: Env,
  corsHeaders: HeadersInit,
): Promise<Response> {
  if (!env.OBSERVATION_DB) {
    return json({ error: 'storage_not_configured' }, corsHeaders, 503);
  }
  const row = await env.OBSERVATION_DB.prepare(
    'SELECT image_key FROM observations WHERE id = ?',
  ).bind(observationId).first<{ image_key: string }>();
  if (!row) {
    return json({ error: 'not_found' }, corsHeaders, 404);
  }
  const image = await getStoredObservationImage(env, row.image_key);
  if (!image) {
    return json({ error: 'image_not_found' }, corsHeaders, 404);
  }
  const headers = new Headers(corsHeaders);
  headers.set('content-type', image.contentType);
  headers.set('cache-control', 'private, max-age=3600');
  return new Response(image.body, { headers });
}

async function updateObservationReview(
  observationId: string,
  request: Request,
  env: Env,
  corsHeaders: HeadersInit,
): Promise<Response> {
  if (!env.OBSERVATION_DB) {
    return json({ error: 'storage_not_configured' }, corsHeaders, 503);
  }
  const row = await env.OBSERVATION_DB.prepare(
    'SELECT * FROM observations WHERE id = ?',
  ).bind(observationId).first<ObservationRow>();
  if (!row) {
    return json({ error: 'not_found' }, corsHeaders, 404);
  }

  const body = await request.json<{
    action?: string;
    species_id?: string;
  }>();
  if (body.action !== 'confirm' && !(await isAuthorized(request, env))) {
    return json({ error: 'unauthorized' }, corsHeaders, 401);
  }
  const now = new Date().toISOString();

  if (body.action === 'confirm') {
    const speciesIdValue = typeof body.species_id === 'string' ? body.species_id.trim() : '';
    const candidates = safeJson<CandidateSpeciesResult[]>(row.candidate_species_json, []);
    if (!speciesIdValue || !candidates.some((candidate) => candidate.species_id === speciesIdValue)) {
      return json({ error: 'species_not_in_candidates' }, corsHeaders, 400);
    }
    await env.OBSERVATION_DB.prepare(
      `UPDATE observations
       SET confirmed_species_id = ?,
           status = 'confirmed',
           updated_at = ?
       WHERE id = ?`,
    ).bind(speciesIdValue, now, observationId).run();
    return json({ ok: true, status: 'confirmed', observation_id: observationId }, corsHeaders);
  }

  if (body.action === 'reject') {
    await updateObservationStatus(env.OBSERVATION_DB, observationId, 'rejected');
    return json({ ok: true, status: 'rejected', observation_id: observationId }, corsHeaders);
  }

  if (body.action === 'needs_review') {
    await updateObservationStatus(env.OBSERVATION_DB, observationId, 'needs_review');
    return json({ ok: true, status: 'needs_review', observation_id: observationId }, corsHeaders);
  }

  if (body.action === 'needs_retake') {
    await updateObservationStatus(env.OBSERVATION_DB, observationId, 'needs_retake');
    return json({ ok: true, status: 'needs_retake', observation_id: observationId }, corsHeaders);
  }

  return json({ error: 'invalid_action' }, corsHeaders, 400);
}

async function classifyPersistedObservation(
  env: Env,
  input: ClassificationInput,
): Promise<void> {
  if (!env.OBSERVATION_DB) {
    return;
  }
  await updateObservationStatus(env.OBSERVATION_DB, input.observationId, 'classifying');
  try {
    await seedSpeciesCatalog(env.OBSERVATION_DB);
    const classifier = getClassifier(env);
    const result = await classifier.classify(input, env);
    const nextStatus = result.needs_retake
      ? 'needs_retake'
      : result.candidates.length > 0
        ? 'candidate_ready'
        : 'needs_review';
    await env.OBSERVATION_DB.prepare(
      `UPDATE observations
       SET broad_category = ?,
           candidate_species_json = ?,
           status = ?,
           classifier_mode = ?,
           classifier_version = ?,
           updated_at = ?
       WHERE id = ?`,
    ).bind(
      result.broad_category,
      JSON.stringify(result.candidates.slice(0, 3)),
      nextStatus,
      result.classifier_mode,
      result.classifier_version,
      new Date().toISOString(),
      input.observationId,
    ).run();
  } catch (error) {
    console.error(JSON.stringify({
      message: 'classification_failed',
      observationId: input.observationId,
      error: String(error),
    }));
    await updateObservationStatus(env.OBSERVATION_DB, input.observationId, 'classification_failed');
  }
}

function getClassifier(env: Env): SpeciesClassifier {
  return shouldUseOpenAi(env) ? new OpenAIClassifier() : new FreeRuleClassifier();
}

async function seedSpeciesCatalog(db: D1Database): Promise<void> {
  const now = new Date().toISOString();
  const species = [
    ...AI_PLANT_CANDIDATES.map((candidate) => ({ candidate: candidate as CandidateProfile, category: 'plant' as const })),
    ...INSECT_CANDIDATES.map((candidate) => ({ candidate: candidate as CandidateProfile, category: 'insect' as const })),
  ];
  await db.batch(species.map(({ candidate, category }) => db.prepare(
    `INSERT OR IGNORE INTO species (
      id,
      japanese_name,
      scientific_name,
      category,
      description,
      active_months_json,
      habitat_tags_json,
      image_url,
      is_sensitive_location,
      created_at,
      updated_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  ).bind(
    speciesId(candidate),
    candidate.commonName,
    candidate.scientificName,
    category,
    candidate.hint,
    JSON.stringify(candidate.seasonMonths),
    JSON.stringify(candidate.visualKeywords),
    null,
    candidate.safety ? 1 : 0,
    now,
    now,
  )));
}

class FreeRuleClassifier implements SpeciesClassifier {
  async classify(input: ClassificationInput): Promise<ClassificationResult> {
    const broadCategory = inferBroadCategory(input.mlLabels);
    const capturedMonth = new Date(input.capturedAt).getUTCMonth() + 1;
    const candidates = candidateProfilesForBroadCategory(broadCategory)
      .filter((candidate) => candidate.seasonMonths.includes(capturedMonth))
      .slice(0, 3)
      .map((candidate) => ({
        species_id: speciesId(candidate),
        reason: [
          `端末ラベルから「${broadCategory}」のなかまと見ました。`,
          `${capturedMonth}月に見られる候補です。`,
          candidate.hint,
        ].join(' '),
      }));

    return {
      broad_category: broadCategory,
      candidates,
      requires_human_confirmation: true,
      needs_retake: broadCategory === 'unknown' && input.mlLabels.length === 0,
      classifier_mode: 'free',
      classifier_version: 'free-rule-2026-07-20',
    };
  }
}

class OpenAIClassifier implements SpeciesClassifier {
  async classify(input: ClassificationInput, env: Env): Promise<ClassificationResult> {
    const fallback = await new FreeRuleClassifier().classify(input);
    if (!input.imageDataUrl || !env.OPENAI_API_KEY) {
      return fallback;
    }
    const analysis = await analyzeSpeciesPhoto({
      id: input.observationId,
      category: fallback.broad_category === 'plant' || fallback.broad_category === 'tree' || fallback.broad_category === 'flower'
        ? 'plant'
        : fallback.broad_category === 'unknown'
          ? 'unknown'
          : 'insect',
      label: input.mlLabels[0]?.text ?? fallback.broad_category,
      confidence: input.mlLabels[0]?.confidence ?? null,
      latitude: input.latitude,
      longitude: input.longitude,
      observedAt: input.capturedAt,
      photoDataUrl: input.imageDataUrl,
    }, input.imageDataUrl, env);
    if (!analysis || analysis.category === 'unknown') {
      return {
        ...fallback,
        classifier_mode: 'openai',
        classifier_version: openAiClassifierVersion(env),
      };
    }
    const matched = analysis.category === 'plant'
      ? findPlantCandidate(analysis.commonName, analysis.scientificName ?? null)
      : findInsectCandidate(analysis.commonName, analysis.scientificName ?? null);
    if (!matched) {
      return {
        ...fallback,
        classifier_mode: 'openai',
        classifier_version: openAiClassifierVersion(env),
      };
    }
    return {
      broad_category: analysis.category === 'plant'
        ? inferPlantBroadCategory(matched)
        : inferInsectBroadCategory(matched),
      candidates: [{
        species_id: speciesId(matched),
        reason: analysis.reason || '画像AIと神山町向け候補表から候補にしました。',
      }],
      requires_human_confirmation: true,
      needs_retake: false,
      classifier_mode: 'openai',
      classifier_version: openAiClassifierVersion(env),
    };
  }
}

async function findObservationByClient(
  db: D1Database,
  deviceId: string,
  clientObservationId: string,
): Promise<ObservationRow | null> {
  return await db.prepare(
    'SELECT * FROM observations WHERE device_id = ? AND client_observation_id = ?',
  ).bind(deviceId, clientObservationId).first<ObservationRow>();
}

async function putObservationImage(
  env: Env,
  imageKey: string,
  imageBytes: Uint8Array,
  contentType: 'image/jpeg' | 'image/webp',
  metadata: Record<string, string>,
): Promise<void> {
  if (env.OBSERVATION_IMAGES) {
    await env.OBSERVATION_IMAGES.put(imageKey, imageBytes, {
      httpMetadata: { contentType },
      customMetadata: metadata,
    });
    return;
  }
  await env.OBSERVATIONS.put(`image:${imageKey}`, bytesToBase64(imageBytes), {
    metadata: {
      contentType,
      ...metadata,
    },
  });
}

async function deleteObservationImage(env: Env, imageKey: string): Promise<void> {
  if (env.OBSERVATION_IMAGES) {
    await env.OBSERVATION_IMAGES.delete(imageKey);
    return;
  }
  await env.OBSERVATIONS.delete(`image:${imageKey}`);
}

async function getStoredObservationImage(
  env: Env,
  imageKey: string,
): Promise<{ body: ReadableStream | Uint8Array; contentType: string } | null> {
  if (env.OBSERVATION_IMAGES) {
    const object = await env.OBSERVATION_IMAGES.get(imageKey);
    if (!object) {
      return null;
    }
    return {
      body: object.body,
      contentType: object.httpMetadata?.contentType ?? 'image/jpeg',
    };
  }
  const value = await env.OBSERVATIONS.getWithMetadata(`image:${imageKey}`, 'text');
  if (!value.value) {
    return null;
  }
  const metadata = value.metadata as { contentType?: string } | null;
  return {
    body: base64ToBytes(value.value),
    contentType: metadata?.contentType ?? 'image/jpeg',
  };
}

async function insertObservation(db: D1Database, row: ObservationRow): Promise<void> {
  await db.prepare(
    `INSERT INTO observations (
      id,
      client_observation_id,
      device_id,
      captured_at,
      received_at,
      latitude,
      longitude,
      public_latitude,
      public_longitude,
      location_accuracy_m,
      location_visibility,
      image_key,
      image_sha256,
      ml_labels_json,
      broad_category,
      candidate_species_json,
      confirmed_species_id,
      status,
      classifier_mode,
      classifier_version,
      quality_score,
      created_at,
      updated_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  ).bind(
    row.id,
    row.client_observation_id,
    row.device_id,
    row.captured_at,
    row.received_at,
    row.latitude,
    row.longitude,
    row.public_latitude,
    row.public_longitude,
    row.location_accuracy_m,
    row.location_visibility,
    row.image_key,
    row.image_sha256,
    row.ml_labels_json,
    row.broad_category,
    row.candidate_species_json,
    row.confirmed_species_id,
    row.status,
    row.classifier_mode,
    row.classifier_version,
    row.quality_score,
    row.created_at,
    row.updated_at,
  ).run();
}

async function updateObservationStatus(
  db: D1Database,
  observationId: string,
  status: ObservationStatus,
): Promise<void> {
  await db.prepare(
    'UPDATE observations SET status = ?, updated_at = ? WHERE id = ?',
  ).bind(status, new Date().toISOString(), observationId).run();
}

function rowToApiObservation(row: ObservationRow) {
  return {
    id: row.id,
    client_observation_id: row.client_observation_id,
    device_id: row.device_id,
    captured_at: row.captured_at,
    received_at: row.received_at,
    latitude: row.latitude,
    longitude: row.longitude,
    public_latitude: row.public_latitude,
    public_longitude: row.public_longitude,
    location_accuracy_m: row.location_accuracy_m,
    location_visibility: row.location_visibility,
    image_url: `/api/v1/observations/${row.id}/image`,
    image_sha256: row.image_sha256,
    ml_labels: safeJson(row.ml_labels_json, []),
    broad_category: row.broad_category,
    candidates: safeJson(row.candidate_species_json, []),
    confirmed_species_id: row.confirmed_species_id,
    status: row.status,
    classifier_mode: row.classifier_mode,
    classifier_version: row.classifier_version,
    quality_score: row.quality_score,
    updated_at: row.updated_at,
  };
}

function safeJson<T>(value: string | null, fallback: T): T {
  if (!value) {
    return fallback;
  }
  try {
    return JSON.parse(value) as T;
  } catch {
    return fallback;
  }
}

function sanitizeRequiredId(value: MultipartValue | null, fallback: string): string {
  const raw = typeof value === 'string' ? value : fallback;
  return sanitizeId(raw) ?? fallback;
}

function normalizeIsoDate(value: MultipartValue | null): string | null {
  if (typeof value !== 'string') {
    return null;
  }
  const parsed = Date.parse(value);
  return Number.isNaN(parsed) ? null : new Date(parsed).toISOString();
}

function normalizeNumber(value: MultipartValue | null): number | null {
  if (typeof value !== 'string' || value.trim() === '') {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function normalizeLatitude(value: MultipartValue | null): number | null {
  const parsed = normalizeNumber(value);
  return parsed != null && parsed >= -90 && parsed <= 90 ? parsed : null;
}

function normalizeLongitude(value: MultipartValue | null): number | null {
  const parsed = normalizeNumber(value);
  return parsed != null && parsed >= -180 && parsed <= 180 ? parsed : null;
}

function buildPublicPoint(
  latitude: number | null,
  longitude: number | null,
): { latitude: number | null; longitude: number | null } {
  if (latitude == null || longitude == null) {
    return { latitude: null, longitude: null };
  }
  return {
    latitude: Math.round(latitude * 1000) / 1000,
    longitude: Math.round(longitude * 1000) / 1000,
  };
}

function parseMlLabels(value: MultipartValue | null): MlLabel[] {
  if (typeof value !== 'string' || !value.trim()) {
    return [];
  }
  try {
    const parsed = JSON.parse(value) as unknown;
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed
      .map((item): MlLabel | null => {
        if (!item || typeof item !== 'object') {
          return null;
        }
        const text = (item as { text?: unknown }).text;
        const confidence = (item as { confidence?: unknown }).confidence;
        if (typeof text !== 'string' || !text.trim()) {
          return null;
        }
        return {
          text: text.trim(),
          confidence: typeof confidence === 'number' ? clampConfidence(confidence) : null,
        };
      })
      .filter((item): item is MlLabel => item != null)
      .slice(0, 8);
  } catch {
    return [];
  }
}

function isUploadedFile(value: unknown): value is File {
  return Boolean(
    value &&
    typeof value !== 'string' &&
    typeof (value as File).arrayBuffer === 'function' &&
    typeof (value as File).size === 'number',
  );
}

function detectImageType(
  bytes: Uint8Array,
  contentType: string,
): { contentType: 'image/jpeg' | 'image/webp'; extension: 'jpg' | 'webp' } | null {
  const isJpeg = bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
  if (isJpeg) {
    return { contentType: 'image/jpeg', extension: 'jpg' };
  }
  const isWebp = bytes.length >= 12 &&
    bytes[0] === 0x52 &&
    bytes[1] === 0x49 &&
    bytes[2] === 0x46 &&
    bytes[3] === 0x46 &&
    bytes[8] === 0x57 &&
    bytes[9] === 0x45 &&
    bytes[10] === 0x42 &&
    bytes[11] === 0x50;
  if (isWebp) {
    return { contentType: 'image/webp', extension: 'webp' };
  }
  if (contentType === 'image/jpeg') {
    return { contentType: 'image/jpeg', extension: 'jpg' };
  }
  if (contentType === 'image/webp') {
    return { contentType: 'image/webp', extension: 'webp' };
  }
  return null;
}

async function sha256Hex(bytes: Uint8Array): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('');
}

function buildDataUrlFromBytes(bytes: Uint8Array, contentType: string): string {
  return `data:${contentType};base64,${bytesToBase64(bytes)}`;
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = '';
  const chunkSize = 0x8000;
  for (let offset = 0; offset < bytes.length; offset += chunkSize) {
    const chunk = bytes.slice(offset, offset + chunkSize);
    binary += String.fromCharCode(...chunk);
  }
  return btoa(binary);
}

function base64ToBytes(value: string): Uint8Array {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
}

function parseUploadLimit(env: Env): number {
  const parsed = Number(env.MAX_UPLOAD_BYTES);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : DEFAULT_MAX_UPLOAD_BYTES;
}

function inferBroadCategory(labels: MlLabel[]): BroadCategory {
  const signal = labels.map((label) => label.text).join(' ').toLowerCase();
  if (!signal.trim()) {
    return 'unknown';
  }
  if (signal.includes('mushroom') || signal.includes('fungus') || signal.includes('きのこ')) {
    return 'mushroom';
  }
  if (signal.includes('butterfly') || signal.includes('moth') || signal.includes('チョウ') || signal.includes('蝶')) {
    return 'butterfly';
  }
  if (signal.includes('beetle') || signal.includes('カブト') || signal.includes('クワガタ') || signal.includes('甲虫')) {
    return 'beetle';
  }
  if (signal.includes('insect') || signal.includes('bee') || signal.includes('wasp') || signal.includes('bug') || signal.includes('dragonfly')) {
    return 'insect';
  }
  if (signal.includes('flower') || signal.includes('花')) {
    return 'flower';
  }
  if (signal.includes('tree') || signal.includes('樹') || signal.includes('木')) {
    return 'tree';
  }
  if (signal.includes('plant') || signal.includes('leaf') || signal.includes('grass') || signal.includes('fern') || signal.includes('herb')) {
    return 'plant';
  }
  return 'unknown';
}

function candidateProfilesForBroadCategory(category: BroadCategory): readonly CandidateProfile[] {
  if (category === 'butterfly') {
    return INSECT_CANDIDATES.filter((candidate) => candidate.commonName.includes('チョウ') || candidate.aliases.some((alias) => alias.includes('蝶')));
  }
  if (category === 'beetle') {
    return INSECT_CANDIDATES.filter((candidate) => candidate.commonName.includes('カブト') || candidate.commonName.includes('クワガタ'));
  }
  if (category === 'insect') {
    return INSECT_CANDIDATES;
  }
  if (category === 'flower' || category === 'tree' || category === 'plant') {
    return AI_PLANT_CANDIDATES;
  }
  return [];
}

function inferPlantBroadCategory(candidate: CandidateProfile): BroadCategory {
  const text = [
    candidate.commonName,
    candidate.hint,
    ...candidate.visualKeywords,
  ].join(' ');
  if (text.includes('花')) {
    return 'flower';
  }
  if (text.includes('木') || text.includes('樹') || text.includes('低木')) {
    return 'tree';
  }
  return 'plant';
}

function inferInsectBroadCategory(candidate: CandidateProfile): BroadCategory {
  if (candidate.commonName.includes('チョウ') || candidate.aliases.some((alias) => alias.includes('蝶'))) {
    return 'butterfly';
  }
  if (candidate.commonName.includes('カブト') || candidate.commonName.includes('クワガタ')) {
    return 'beetle';
  }
  return 'insect';
}

function speciesId(candidate: CandidateProfile): string {
  return candidate.scientificName
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '');
}

function openAiClassifierVersion(env: Env): string {
  return `openai-${env.OPENAI_MODEL || DEFAULT_OPENAI_MODEL}-2026-07-20`;
}

async function listObservations(
  url: URL,
  env: Env,
  corsHeaders: HeadersInit,
): Promise<Response> {
  const since = Number(url.searchParams.get('since') ?? '0');
  const listed = await env.OBSERVATIONS.list({ prefix: 'obs:', limit: 1000 });
  const observations: ThinkletObservationPayload[] = [];

  for (const key of listed.keys) {
    const metadata = key.metadata as { receivedAt?: number } | undefined;
    if (metadata?.receivedAt && metadata.receivedAt <= since) {
      continue;
    }
    const raw = await env.OBSERVATIONS.get(key.name);
    if (!raw) {
      continue;
    }
    observations.push(JSON.parse(raw) as ThinkletObservationPayload);
  }

  observations.sort((a, b) => Number(a.receivedAt ?? 0) - Number(b.receivedAt ?? 0));
  return json({ observations, serverTime: Date.now() }, corsHeaders);
}

async function isAuthorized(request: Request, env: Env): Promise<boolean> {
  if (!env.SYNC_WRITE_TOKEN) {
    return true;
  }
  const header = request.headers.get('authorization') ?? '';
  const token = header.startsWith('Bearer ') ? header.slice('Bearer '.length) : '';
  return await timingSafeEqual(token, env.SYNC_WRITE_TOKEN);
}

async function timingSafeEqual(a: string, b: string): Promise<boolean> {
  const encoder = new TextEncoder();
  const left = encoder.encode(a);
  const right = encoder.encode(b);
  if (left.length !== right.length) {
    return false;
  }
  const key = await crypto.subtle.importKey(
    'raw',
    encoder.encode('kamiyama-sync-compare-key'),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign'],
  );
  const leftMac = await crypto.subtle.sign('HMAC', key, left);
  const rightMac = await crypto.subtle.sign('HMAC', key, right);
  return equalBytes(new Uint8Array(leftMac), new Uint8Array(rightMac));
}

function equalBytes(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) {
    return false;
  }
  let diff = 0;
  for (let index = 0; index < a.length; index += 1) {
    diff |= a[index] ^ b[index];
  }
  return diff === 0;
}

function buildCorsHeaders(request: Request, env: Env): HeadersInit {
  const origin = request.headers.get('origin') ?? '';
  const allowedOrigins = (env.ALLOWED_ORIGINS ?? '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
  const allowedOrigin = allowedOrigins.includes(origin) ? origin : allowedOrigins[0] ?? '*';
  return {
    'access-control-allow-origin': allowedOrigin,
    'access-control-allow-methods': 'GET,POST,OPTIONS',
    'access-control-allow-headers': 'content-type,authorization',
    'access-control-max-age': '86400',
  };
}

function json(data: unknown, corsHeaders: HeadersInit, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      ...JSON_HEADERS,
      ...corsHeaders,
    },
  });
}

function sanitizeId(id: unknown): string | null {
  if (typeof id !== 'string') {
    return null;
  }
  const normalized = id.trim().replace(/[^a-zA-Z0-9_-]/g, '-').slice(0, 96);
  return normalized || null;
}

function normalizeObservedAt(value: unknown): number {
  if (typeof value === 'number') {
    return value;
  }
  if (typeof value === 'string') {
    const parsed = Date.parse(value);
    if (!Number.isNaN(parsed)) {
      return parsed;
    }
  }
  return Date.now();
}

function normalizeCategory(value: unknown): 'plant' | 'insect' | 'unknown' {
  if (value === 'insect' || value === 'plant') {
    return value;
  }
  return 'unknown';
}

function buildPhotoDataUrl(payload: ThinkletObservationPayload): string | null {
  if (payload.photoDataUrl?.startsWith('data:image/')) {
    return payload.photoDataUrl;
  }
  if (!payload.photoBase64) {
    return null;
  }
  const mimeType = payload.photoMimeType?.startsWith('image/')
    ? payload.photoMimeType
    : 'image/jpeg';
  return `data:${mimeType};base64,${payload.photoBase64}`;
}

function getAiMode(env: Env): 'free' | 'openai' {
  return env.AI_MODE === 'openai' ? 'openai' : DEFAULT_AI_MODE;
}

function shouldUseOpenAi(env: Env): boolean {
  return getAiMode(env) === 'openai' && Boolean(env.OPENAI_API_KEY);
}

async function analyzeSpeciesPhoto(
  payload: ThinkletObservationPayload,
  photoDataUrl: string | null,
  env: Env,
): Promise<SpeciesAnalysis | null> {
  if (!photoDataUrl || !shouldUseOpenAi(env)) {
    return null;
  }

  const referenceImages = await loadReferenceImages(env);
  const referenceContent = referenceImages.flatMap((reference, index) => [
    {
      type: 'input_text' as const,
      text: [
        `参考写真${index + 1}: ${reference.candidate.commonName} / ${reference.candidate.scientificName}`,
        `出典: ${reference.source}${reference.license ? ` / license: ${reference.license}` : ''}`,
        `見分け方: ${reference.candidate.visualKeywords.join('、')}`,
      ].join('\n'),
    },
    {
      type: 'input_image' as const,
      image_url: reference.url,
    },
  ]);

  const response = await fetch('https://api.openai.com/v1/responses', {
    method: 'POST',
    headers: {
      authorization: `Bearer ${env.OPENAI_API_KEY}`,
      'content-type': 'application/json',
    },
    body: JSON.stringify({
      model: env.OPENAI_MODEL || DEFAULT_OPENAI_MODEL,
      input: [
        {
          role: 'user',
          content: [
            {
              type: 'input_text',
              text: [
                '神山町の小学生向け自然観察アプリに登録するため、この写真の生物を推定してください。',
                'これは授業や探検で使うため、断定しすぎず「AIのよそう」として安全で短い表現にしてください。',
                '虫または植物が写っている場合は、下の候補表と照合してください。',
                '候補表と参考写真に強く一致する生き物は、その日本語名・学名・レア度を返してください。',
                '参考写真はGBIFの公開観察写真です。観察写真そのものと参考写真を比較して、色、形、模様、葉・翅・実の特徴を優先してください。',
                '端末側の簡易ラベルより、画像と候補表の一致を優先してください。',
                '自信が低い場合は無理に候補名へ寄せず、未同定の植物または未同定の虫にしてください。',
                '候補表にない虫や植物でも、写真から一般的な種名を高い自信で判断できる場合は、その日本語名と学名候補を返してください。',
                '候補表にも一般的な種名にも自信がない虫は commonName を「未同定の虫」、scientificName を null、rarity を "common" にしてください。',
                '候補表にも一般的な種名にも自信がない植物は commonName を「未同定の植物」、scientificName を null、rarity を "common" にしてください。',
                '植物または虫ではない場合は category を "unknown" にしてください。',
                '危険な虫の可能性がある場合は、reason に「近づかず先生に知らせる」ことを短く含めてください。',
                `植物候補表: ${JSON.stringify(toPromptCandidates(AI_PLANT_CANDIDATES))}`,
                `昆虫候補表: ${JSON.stringify(toPromptCandidates(INSECT_CANDIDATES))}`,
                `参考写真数: ${referenceImages.length}`,
                'JSONだけを返してください。',
                '{"category":"plant|insect|unknown","commonName":"日本語名または未同定の植物/虫","scientificName":"学名またはnull","rarity":"common|uncommon|rare|special|null","confidence":0.0,"reason":"短い根拠"}',
                `端末側の簡易ラベル: ${payload.label ?? 'なし'}`,
              ].join('\n'),
            },
            {
              type: 'input_image',
              image_url: photoDataUrl,
            },
            ...referenceContent,
          ],
        },
      ],
      max_output_tokens: 500,
    }),
  });

  if (!response.ok) {
    console.error(JSON.stringify({
      message: 'openai_analysis_failed',
      status: response.status,
      body: await response.text(),
    }));
    return null;
  }

  const data = await response.json<Record<string, unknown>>();
  const outputText = extractOutputText(data);
  const parsed = parseJsonObject(outputText);
  if (!parsed) {
    console.error(JSON.stringify({ message: 'openai_analysis_parse_failed', outputText }));
    return null;
  }
  if (!validateSpeciesAnalysisSchema(parsed)) {
    console.error(JSON.stringify({
      message: 'openai_analysis_schema_failed',
      schema: SPECIES_ANALYSIS_SCHEMA,
      outputText,
    }));
    return null;
  }

  const category = parsed.category === 'insect' || parsed.category === 'plant'
    ? parsed.category
    : 'unknown';
  const commonName = typeof parsed.commonName === 'string' && parsed.commonName.trim()
    ? parsed.commonName.trim()
    : payload.label ?? '未同定';
  const scientificName = typeof parsed.scientificName === 'string' && parsed.scientificName.trim()
    ? parsed.scientificName.trim()
    : null;
  const matchedInsect = category === 'insect'
    ? findInsectCandidate(commonName, scientificName)
    : null;
  const matchedPlant = category === 'plant'
    ? findPlantCandidate(commonName, scientificName)
    : null;
  const rarity = matchedInsect?.rarity
    ?? matchedPlant?.rarity
    ?? normalizeRarity(parsed.rarity)
    ?? (category === 'insect' || category === 'plant' ? 'common' : null);
  const confidence = typeof parsed.confidence === 'number'
    ? Math.max(0, Math.min(1, parsed.confidence))
    : 0.5;
  const reason = typeof parsed.reason === 'string' && parsed.reason.trim()
    ? parsed.reason.trim()
    : '画像AIによる推定です。';

  return {
    category,
    commonName: matchedInsect?.commonName ?? matchedPlant?.commonName ?? commonName,
    scientificName: matchedInsect?.scientificName ?? matchedPlant?.scientificName ?? scientificName,
    rarity,
    confidence,
    reason,
  };
}

function toPromptCandidates(candidates: readonly CandidateProfile[]) {
  return candidates.map((candidate) => ({
    commonName: candidate.commonName,
    aliases: candidate.aliases,
    scientificName: candidate.scientificName,
    rarity: candidate.rarity,
    hint: candidate.hint,
    visualKeywords: candidate.visualKeywords,
    seasonMonths: candidate.seasonMonths,
    safety: candidate.safety,
  }));
}

async function loadReferenceImages(env: Env): Promise<CandidateReferenceImage[]> {
  const candidateGroups = [
    AI_PLANT_CANDIDATES.slice(0, MAX_REFERENCE_IMAGES_PER_CATEGORY),
    INSECT_CANDIDATES.slice(0, MAX_REFERENCE_IMAGES_PER_CATEGORY),
  ];
  const loaded = await Promise.all(
    candidateGroups.flatMap((candidates) => (
      candidates.map((candidate) => loadReferenceImage(candidate, env))
    )),
  );
  return loaded.filter((item): item is CandidateReferenceImage => item != null);
}

async function loadReferenceImage(
  candidate: CandidateProfile,
  env: Env,
): Promise<CandidateReferenceImage | null> {
  const cacheKey = `ref-image:${candidate.scientificName}`;
  const cached = await env.OBSERVATIONS.get(cacheKey);
  if (cached) {
    try {
      const parsed = JSON.parse(cached) as CandidateReferenceImage;
      if (parsed.url?.startsWith('https://')) {
        return { ...parsed, candidate };
      }
    } catch {
      // Ignore a malformed cache entry and refresh it below.
    }
  }

  const reference = await fetchGbifReferenceImage(candidate);
  if (!reference) {
    return null;
  }
  await env.OBSERVATIONS.put(cacheKey, JSON.stringify(reference), {
    expirationTtl: REFERENCE_IMAGE_CACHE_TTL_SECONDS,
  });
  return reference;
}

async function fetchGbifReferenceImage(
  candidate: CandidateProfile,
): Promise<CandidateReferenceImage | null> {
  const url = new URL('https://api.gbif.org/v1/occurrence/search');
  url.searchParams.set('scientificName', candidate.scientificName);
  url.searchParams.set('mediaType', 'StillImage');
  url.searchParams.set('hasCoordinate', 'true');
  url.searchParams.set('limit', '8');

  try {
    const response = await fetch(url.toString(), {
      headers: { accept: 'application/json' },
    });
    if (!response.ok) {
      console.error(JSON.stringify({
        message: 'gbif_reference_fetch_failed',
        scientificName: candidate.scientificName,
        status: response.status,
      }));
      return null;
    }

    const data = await response.json<{
      results?: Array<{
        media?: Array<{
          type?: string;
          identifier?: string;
          license?: string;
        }>;
      }>;
    }>();
    const media = data.results
      ?.flatMap((result) => result.media ?? [])
      .find((item) => (
        item.type === 'StillImage' &&
        typeof item.identifier === 'string' &&
        item.identifier.startsWith('https://') &&
        isReusableImageLicense(item.license)
      ));

    if (!media?.identifier) {
      return null;
    }
    return {
      candidate,
      url: media.identifier,
      source: 'GBIF',
      license: media.license,
    };
  } catch (error) {
    console.error(JSON.stringify({
      message: 'gbif_reference_fetch_error',
      scientificName: candidate.scientificName,
      error: String(error),
    }));
    return null;
  }
}

function isReusableImageLicense(license: string | undefined): boolean {
  if (!license) {
    return false;
  }
  const normalized = license.toLowerCase();
  return normalized.includes('creativecommons.org') ||
    normalized.includes('cc0') ||
    normalized.includes('publicdomain');
}

function inferSpeciesFromDeviceSignal(payload: ThinkletObservationPayload): SpeciesAnalysis | null {
  const signal = [
    payload.label,
    payload.aiLabel,
    payload.category,
  ].filter(Boolean).join(' ').toLowerCase();

  const beetleCandidate = matchDeviceSignalToInsect(signal);
  if (!beetleCandidate) {
    return null;
  }

  return {
    category: 'insect',
    commonName: beetleCandidate.commonName,
    scientificName: beetleCandidate.scientificName,
    rarity: beetleCandidate.rarity,
    confidence: clampConfidence(payload.confidence ?? payload.aiConfidence ?? 0.62),
    reason: '端末の簡易ラベルからMVP用に候補を補いました。写真AIを有効にすると精度が上がります。',
  };
}

function matchDeviceSignalToInsect(signal: string): CandidateProfile | null {
  const normalized = signal
    .replace(/[＿_\\-]/g, ' ')
    .replace(/\\s+/g, ' ')
    .trim();

  if (!normalized) {
    return null;
  }

  if (
    normalized.includes('stag beetle') ||
    normalized.includes('クワガタ') ||
    normalized.includes('鍬形')
  ) {
    return findInsectCandidate('ノコギリクワガタ', 'Prosopocoilus inclinatus');
  }

  if (
    normalized.includes('rhinoceros beetle') ||
    normalized.includes('カブト') ||
    normalized.includes('甲虫') ||
    normalized.includes('beetle')
  ) {
    return findInsectCandidate('カブトムシ', 'Trypoxylus dichotomus');
  }

  return null;
}

function clampConfidence(value: number): number {
  return Math.max(0, Math.min(1, value));
}

function findPlantCandidate(
  commonName: string,
  scientificName: string | null,
): CandidateProfile | null {
  return AI_PLANT_CANDIDATES.find((candidate) => (
    candidate.commonName === commonName ||
    (candidate.aliases as readonly string[]).includes(commonName) ||
    (scientificName != null && candidate.scientificName === scientificName)
  )) ?? null;
}

function findInsectCandidate(
  commonName: string,
  scientificName: string | null,
): CandidateProfile | null {
  return INSECT_CANDIDATES.find((candidate) => (
    candidate.commonName === commonName ||
    (candidate.aliases as readonly string[]).includes(commonName) ||
    (scientificName != null && (
      candidate.scientificName === scientificName ||
      scientificName.startsWith(`${candidate.scientificName} `)
    ))
  )) ?? null;
}

function normalizeRarity(value: unknown): RarityValue | null {
  return value === 'common' ||
    value === 'uncommon' ||
    value === 'rare' ||
    value === 'special'
    ? value
    : null;
}

function extractOutputText(data: Record<string, unknown>): string {
  if (typeof data.output_text === 'string') {
    return data.output_text;
  }
  const output = Array.isArray(data.output) ? data.output : [];
  return output
    .flatMap((item) => {
      if (!item || typeof item !== 'object') {
        return [];
      }
      const content = (item as { content?: unknown }).content;
      return Array.isArray(content) ? content : [];
    })
    .map((content) => {
      if (!content || typeof content !== 'object') {
        return '';
      }
      const text = (content as { text?: unknown }).text;
      return typeof text === 'string' ? text : '';
    })
    .join('\n')
    .trim();
}

function parseJsonObject(value: string): Record<string, unknown> | null {
  const cleaned = value
    .trim()
    .replace(/^```(?:json)?/i, '')
    .replace(/```$/i, '')
    .trim();
  const start = cleaned.indexOf('{');
  const end = cleaned.lastIndexOf('}');
  if (start === -1 || end === -1 || end <= start) {
    return null;
  }
  try {
    const parsed = JSON.parse(cleaned.slice(start, end + 1));
    return parsed && typeof parsed === 'object' ? parsed as Record<string, unknown> : null;
  } catch {
    return null;
  }
}

function validateSpeciesAnalysisSchema(value: Record<string, unknown>): boolean {
  const category = value.category;
  const commonName = value.commonName;
  const scientificName = value.scientificName;
  const rarity = value.rarity;
  const confidence = value.confidence;
  const reason = value.reason;
  return (
    (category === 'plant' || category === 'insect' || category === 'unknown') &&
    typeof commonName === 'string' &&
    commonName.trim().length > 0 &&
    (scientificName == null || typeof scientificName === 'string') &&
    (rarity == null || rarity === 'common' || rarity === 'uncommon' || rarity === 'rare' || rarity === 'special') &&
    typeof confidence === 'number' &&
    confidence >= 0 &&
    confidence <= 1 &&
    typeof reason === 'string' &&
    reason.trim().length > 0
  );
}
