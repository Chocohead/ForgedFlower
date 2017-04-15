// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.struct;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructGenericSignatureAttribute;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.struct.gen.generics.GenericFieldDescriptor;
import org.jetbrains.java.decompiler.struct.gen.generics.GenericMain;
import org.jetbrains.java.decompiler.util.DataInputFullStream;

import java.io.IOException;

/*
  field_info {
    u2 access_flags;
    u2 name_index;
    u2 descriptor_index;
    u2 attributes_count;
    attribute_info attributes[attributes_count];
   }
*/
public class StructField extends StructMember {

  private final String name;
  private final String descriptor;
  private GenericFieldDescriptor signature;

  public StructField(DataInputFullStream in, StructClass clStruct) throws IOException {
    accessFlags = in.readUnsignedShort();
    int nameIndex = in.readUnsignedShort();
    int descriptorIndex = in.readUnsignedShort();

    ConstantPool pool = clStruct.getPool();
    String[] values = pool.getClassElement(ConstantPool.FIELD, clStruct.qualifiedName, nameIndex, descriptorIndex);
    name = values[0];
    descriptor = values[1];

    attributes = readAttributes(in, pool);
  }

  public String getName() {
    return name;
  }

  public String getDescriptor() {
    return descriptor;
  }

  @Override
  public String toString() {
    return name;
  }

  public GenericFieldDescriptor getSignature() {
    return signature;
  }

  @Override
  protected StructGeneralAttribute readAttribute(DataInputFullStream in, ConstantPool pool, String name) throws IOException {
    StructGeneralAttribute attribute = super.readAttribute(in, pool, name);
    if ("Signature".equals(name) && DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES)) {
      StructGenericSignatureAttribute signature = (StructGenericSignatureAttribute)attribute;
      this.signature = GenericMain.parseFieldSignature(signature.getSignature());
    }
    return attribute;
  }
}
