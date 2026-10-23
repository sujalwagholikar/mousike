"""Multilingual metadata vectors with a zero-download fallback.

The default encoder maps multilingual music concepts into shared dimensions and
adds Unicode word/subword features. It is NOT an acoustic or neural model.
Set MOUSIKE_EMBEDDINGS=transformer to use actual Sentence Transformer embeddings;
a failed/absent model always falls back and is reported in /api/capabilities.
"""

from __future__ import annotations
from collections import OrderedDict
import hashlib
import math
import os
import threading
import unicodedata

CONCEPTS = {
    "dreamy": (
        "dreamy",
        "dream pop",
        "ethereal",
        "atmospheric",
        "स्वप्निल",
        "सपनीला",
        "ensoñador",
        "rêveur",
        "몽환",
        "夢幻",
    ),
    "chill": (
        "chill",
        "relaxed",
        "relaxing",
        "calm",
        "soft",
        "शांत",
        "सुकून",
        "सुकूनभरा",
        "tranquilo",
        "détente",
        "차분",
        "穏やか",
    ),
    "energetic": (
        "energetic",
        "energy",
        "upbeat",
        "high energy",
        "जोश",
        "ऊर्जावान",
        "उत्साह",
        "enérgico",
        "énergique",
        "활기",
        "元気",
    ),
    "romantic": (
        "romantic",
        "love",
        "प्रेम",
        "प्यार",
        "रोमांटिक",
        "romántico",
        "amour",
        "사랑",
        "愛",
    ),
    "melancholic": (
        "sad",
        "melancholic",
        "heartbreak",
        "bittersweet",
        "उदास",
        "दर्द",
        "विरह",
        "triste",
        "슬픈",
        "悲しい",
    ),
    "focus": (
        "focus",
        "study",
        "concentration",
        "work",
        "ध्यान",
        "पढ़ाई",
        "अभ्यास",
        "estudiar",
        "集中",
        "공부",
    ),
    "sleep": (
        "sleep",
        "night",
        "late night",
        "midnight",
        "रात",
        "नींद",
        "noche",
        "nuit",
        "밤",
        "夜",
    ),
    "party": (
        "party",
        "dance",
        "club",
        "celebration",
        "नाच",
        "जश्न",
        "fiesta",
        "danse",
        "댄스",
        "ダンス",
    ),
    "workout": (
        "workout",
        "gym",
        "running",
        "exercise",
        "कसरत",
        "व्यायाम",
        "entrenamiento",
        "운동",
        "運動",
    ),
    "road trip": (
        "road trip",
        "driving",
        "travel",
        "journey",
        "सफ़र",
        "सफर",
        "यात्रा",
        "viaje",
        "voyage",
        "여행",
        "旅",
    ),
    "nostalgic": (
        "nostalgic",
        "retro",
        "oldies",
        "classic",
        "यादें",
        "पुराने",
        "nostalgia",
        "懐かしい",
    ),
    "indie": ("indie", "independent", "इंडी", "इन्डी", "독립"),
    "pop": ("pop", "पॉप", "팝", "ポップ"),
    "rock": ("rock", "रॉक", "록", "ロック"),
    "hip hop": ("hip hop", "hip-hop", "rap", "रैप", "हिप हॉप", "힙합", "ラップ"),
    "r&b": ("r&b", "rhythm and blues", "neo soul", "soul", "आर एंड बी"),
    "folk": ("folk", "acoustic", "लोक", "लोकगीत", "लोकसंगीत", "民謡"),
    "electronic": ("electronic", "edm", "electronica", "synth", "इलेक्ट्रॉनिक", "電子"),
    "classical": (
        "classical",
        "orchestral",
        "शास्त्रीय",
        "क्लासिकल",
        "clásica",
        "classique",
        "클래식",
        "クラシック",
    ),
    "jazz": ("jazz", "bossa nova", "जैज़", "जैज", "재즈", "ジャズ"),
    "lofi": ("lofi", "lo-fi", "lo fi", "chillhop", "लोफाई", "लो फाई"),
    "ambient": ("ambient", "instrumental", "piano", "वाद्य", "वाद्यसंगीत", "アンビエント"),
    "bollywood": ("bollywood", "बॉलीवुड", "बालीवुड", "हिंदी फिल्म", "हिन्दी फिल्म"),
    "devotional": ("devotional", "bhajan", "भजन", "भक्ति", "भक्ती", "प्रार्थना"),
    "hindi": ("hindi", "हिंदी", "हिन्दी"),
    "english": ("english", "अंग्रेजी", "इंग्रजी", "inglés", "anglais", "영어", "英語"),
    "marathi": ("marathi", "मराठी"),
    "punjabi": ("punjabi", "पंजाबी", "ਪੰਜਾਬੀ"),
    "tamil": ("tamil", "तमिल", "தமிழ்"),
    "telugu": ("telugu", "तेलुगु", "తెలుగు"),
    "kannada": ("kannada", "कन्नड़", "ಕನ್ನಡ"),
    "malayalam": ("malayalam", "मलयालम", "മലയാളം"),
    "bengali": ("bengali", "बंगाली", "বাংলা"),
    "gujarati": ("gujarati", "गुजराती", "ગુજરાતી"),
    "urdu": ("urdu", "उर्दू", "اردو"),
    "spanish": ("spanish", "español", "espanol", "स्पेनिश"),
    "korean": ("korean", "한국어", "코리안", "कोरियाई", "k-pop", "kpop"),
    "japanese": ("japanese", "日本語", "जापानी", "j-pop", "jpop"),
    "french": ("french", "français", "francais", "फ्रेंच"),
    "arabic": ("arabic", "عربي", "अरबी"),
}
GENRES = set(
    "indie pop rock folk electronic classical jazz lofi ambient bollywood devotional".split()
) | {"hip hop", "r&b"}
LANGUAGES = set(
    "hindi english marathi punjabi tamil telugu kannada malayalam bengali gujarati urdu spanish korean japanese french arabic".split()
)
MOODS = set(CONCEPTS) - GENRES - LANGUAGES
STOP = set(
    "the a an and or of to for with songs song music audio video official new best me my some play find like please".split()
)


def fold(text):
    text = unicodedata.normalize("NFKC", str(text or "")).casefold()
    # Keep combining marks: stripping matras destroys Devanagari song identity.
    return " ".join(
        "".join(
            c if c.isalnum() or unicodedata.category(c).startswith("M") else " "
            for c in text
        ).split()
    )


def concepts(text):
    value = " " + fold(text) + " "
    found = set()
    for canonical, aliases in CONCEPTS.items():
        if any(" " + fold(a) + " " in value for a in aliases):
            found.add(canonical)
    return found


def music_tags(track):
    values = [track.get("title", ""), track.get("artist", "")]
    for key in ("genre", "genres", "mood", "moods", "language", "languages", "tags"):
        value = track.get(key, "")
        values.extend(value if isinstance(value, list) else [value])
    return concepts(" ".join(str(v) for v in values))


def metadata_text(track):
    tags = sorted(music_tags(track))
    return " ".join(
        [
            str(track.get("title") or ""),
            str(track.get("artist") or ""),
            str(track.get("album") or ""),
            " ".join(tags),
        ]
    )[:700]


class MetadataEncoder:
    def __init__(self):
        self.model = None
        self.mode = "multilingual-concept-vectors"
        self.error = ""
        self.cache = OrderedDict()
        self.lock = threading.RLock()
        self.inference_lock = threading.Lock()

    def warm(self):
        if os.getenv("MOUSIKE_EMBEDDINGS", "concept") != "transformer":
            return
        try:
            from sentence_transformers import SentenceTransformer

            model = os.getenv(
                "MOUSIKE_EMBEDDING_MODEL",
                "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
            )
            # Explicit administrator opt-in permits the model's first download.
            self.model = SentenceTransformer(
                model, device="cpu", trust_remote_code=False
            )
            self.mode = "sentence-transformer-multilingual"
            with self.lock:
                self.cache.clear()
        except Exception:
            self.error = (
                "Multilingual neural model unavailable; concept vectors active."
            )

    @staticmethod
    def concept_vector(text):
        vector = [0.0] * 384

        def put(token, weight):
            h = hashlib.blake2b(token.encode(), digest_size=8).digest()
            vector[int.from_bytes(h[:4], "big") % 384] += weight * (
                1 if h[4] & 1 else -1
            )

        for tag in concepts(text):
            put("concept:" + tag, 4.0)
        words = [w for w in fold(text).split() if w not in STOP]
        for word in words[:100]:
            put("word:" + word, 1.0)
            if len(word) > 3:
                for n in range(len(word) - 2):
                    put("sub:" + word[n : n + 3], 0.16)
        norm = math.sqrt(sum(x * x for x in vector)) or 1.0
        return tuple(x / norm for x in vector)

    def encode(self, texts):
        texts = [str(t)[:900] for t in texts]
        with self.lock:
            missing = list(
                dict.fromkeys(t for t in texts if (self.mode, t) not in self.cache)
            )
        if missing:
            if self.model is not None:
                try:
                    with self.inference_lock:
                        values = self.model.encode(
                            missing,
                            batch_size=32,
                            normalize_embeddings=True,
                            show_progress_bar=False,
                        )
                    vectors = [tuple(float(x) for x in v) for v in values]
                except Exception:
                    # Never mix 384/other dimensions in one ranking call.
                    return [self.concept_vector(t) for t in texts]
            else:
                vectors = [self.concept_vector(t) for t in missing]
            with self.lock:
                for text, vector in zip(missing, vectors):
                    self.cache[self.mode, text] = vector
                while len(self.cache) > 2048:
                    self.cache.popitem(last=False)
        with self.lock:
            return [
                self.cache.get((self.mode, t)) or self.concept_vector(t) for t in texts
            ]

    @staticmethod
    def similarity(a, b):
        if len(a) != len(b):
            return 0.0
        return max(-1.0, min(1.0, sum(x * y for x, y in zip(a, b))))


ENCODER = MetadataEncoder()
