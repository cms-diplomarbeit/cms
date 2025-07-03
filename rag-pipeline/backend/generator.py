from transformers import pipeline, AutoTokenizer, AutoModelForCausalLM
import torch

def load_generator(model_id="TinyLlama/TinyLlama-1.1B-Chat-v1.0"):
    """
    Loads the TinyLLaMA model for text generation.
    Warning: requires around 2-3 GB RAM with 4-bit quantization.
    """
    tokenizer = AutoTokenizer.from_pretrained(model_id)
    model = AutoModelForCausalLM.from_pretrained(
        model_id,
        device_map="auto",
        load_in_4bit=True,  # spart Ressourcen
        torch_dtype=torch.float16
    )
    generator = pipeline("text-generation", model=model, tokenizer=tokenizer)
    return generator

def build_prompt(context_docs: list[str], question: str) -> str:
    """
    Erstellt einen Prompt aus Kontext + Frage.
    """
    context = "\n".join(context_docs)
    prompt = f"""Beantworte die folgende Frage basierend auf dem Kontext:

{context}

Frage: {question}
Antwort:"""
    return prompt

def generate_answer(generator, prompt: str, max_new_tokens=150) -> str:
    """
    Nutzt das Modell zur Generierung einer Antwort basierend auf dem Prompt.
    """
    output = generator(prompt, max_new_tokens=max_new_tokens, do_sample=True, temperature=0.7)[0]["generated_text"]
    return output[len(prompt):].strip()