from sentence_transformers import SentenceTransformer


def load_encoder():
    """
    Loads our SBERT Encoder
    """
    model = SentenceTransformer("all-MiniLM-L6-v2")
    return model

def encode_documents(model, documents):
    """
    Encodes a list of documents using the provided model
    """
    return model.encode(documents, convert_to_tensor=False)
